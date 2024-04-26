package org.threadlys.threading.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.threadlys.streams.CheckedCallable;
import org.threadlys.streams.CheckedConsumer;
import org.threadlys.streams.CheckedFunction;
import org.threadlys.streams.CheckedPredicate;
import org.threadlys.streams.CheckedRunnable;
import org.threadlys.streams.CheckedSupplier;
import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ParallelStreamFassade;
import org.threadlys.threading.ThreadLocalBean;
import org.threadlys.threading.ThreadLocalTransferrer;
import org.threadlys.threading.ThreadLocalTransferrerRegistry;
import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.ReflectUtil;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.StateRollback;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

//CHECKSTYLE: DeclarationOrderCheck OFF
//CHECKSTYLE: IllegalCatch OFF
@SuppressWarnings({ "PMD.AssignmentInOperand", "checkstyle:DeclarationOrderCheck", "PMD.DoNotUseThreads", "checkstyle:IllegalCatch" })
@RequiredArgsConstructor
public class ContextSnapshotImpl implements ContextSnapshot, ContextSnapshotIntern {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /**
     * Never hold a thread reference strongly to avoid memory leaks
     */
    private final Reference<Thread> ownerR = new WeakReference<>(Thread.currentThread());

    private final int instanceId = SEQUENCE.incrementAndGet();

    final ContextSnapshotController contextSnapshotController;

    final ParallelStreamFassade parallelStreamFassade;

    final ThreadLocalTransferrerRegistry threadLocalTransferrerRegistry;

    final ReflectUtil reflectUtil;

    final SneakyThrowUtil sneakyThrowUtil;

    @Getter
    final TransferrableThreadLocal<?>[] threadLocals;

    @Getter
    final Object[] values;

    @Getter
    final Map<String, ThreadScopeEntry> threadScopeMap;

    @Override
    public <T> Stream<T> parallel(Stream<T> stream) {
        return parallelStreamFassade.parallel(stream);
    }

    @Override
    public <T> Stream<T> parallel(Collection<T> collection) {
        return parallelStreamFassade.parallel(collection);
    }

    @Override
    public <T> Function<T, T> push() {
        return item -> {
            contextSnapshotController.pushContext(this);
            return item;
        };
    }

    @Override
    public <T> Function<T, T> pop() {
        return item -> {
            contextSnapshotController.popContext(this);
            return item;
        };
    }

    @Override
    public IStateRollback apply() {
        return contextSnapshotController.pushContext(this);
    }

    @Override
    public IStateRollback apply(IStateRollback... rollbacks) {
        return StateRollback.prepend(contextSnapshotController.pushContext(this), rollbacks);
    }

    @Override
    public <T> Predicate<T> scopedPredicate(CheckedPredicate<T> predicate) {
        return t -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                return predicate.test(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @Override
    public <T> Consumer<T> scopedConsumer(CheckedConsumer<T> consumer) {
        return t -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                consumer.accept(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @Override
    public <T> Callable<T> scopedCallable(CheckedCallable<T> callable) {
        return () -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                return callable.call();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @Override
    public <T, R> Function<T, R> scoped(CheckedFunction<T, R> function) {
        return t -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                return function.apply(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @Override
    public Runnable scoped(CheckedRunnable runnable) {
        return () -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                runnable.run();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @Override
    public <R> Supplier<R> scoped(CheckedSupplier<R> supplier) {
        return () -> {
            IStateRollback rollback = contextSnapshotController.pushContext(this);
            try {
                return supplier.get();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            } finally {
                rollback.rollback();
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object processCreation(String name, Object newBean) {
        ThreadScopeEntry threadScopeEntry = threadScopeMap.get(name);
        if (threadScopeEntry == null) {
            return newBean;
        }
        Field[] fields = threadScopeEntry.getFields();
        Object[] originalFieldValues = threadScopeEntry.getOriginalValues();
        for (int a = fields.length; a-- > 0;) {
            Field field = fields[a];
            reflectUtil.setFieldValue(field, newBean, originalFieldValues[a]);
        }
        Object originalBean = threadScopeEntry.getOriginalBean();
        List<ThreadLocalTransferrer<?>> threadLocalTransferrers = threadLocalTransferrerRegistry.getThreadLocalTransferrers(newBean.getClass());
        threadLocalTransferrers.forEach(transferrer -> ((ThreadLocalTransferrer<Object>) transferrer).transferFromMasterToFork(originalBean, newBean));
        if (newBean instanceof ThreadLocalBean) {
            ((ThreadLocalBean<Object>) originalBean).fillForkedThreadLocalBean(newBean);
        }
        return newBean;
    }

    @Override
    public String toString() {
        Thread owner = ownerR.get();
        return "ContextSnapshot (id=" + instanceId + ", owner=" + (owner != null ? owner.getId() + "-" + owner.getName() : "n/a") + ")";
    }
}
