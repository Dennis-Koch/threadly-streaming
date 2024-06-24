package org.threadlys.threading.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.threading.ContextSnapshotLifecycleListener;
import org.threadlys.threading.ContextSnapshotLifecycleListenerExtendable;
import org.threadlys.threading.ThreadLocalTransferrerRegistry;
import org.threadlys.threading.Transferrable;
import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocals;
import org.threadlys.utils.StateRevert;
import org.threadlys.utils.ListenersListAdapter;
import org.threadlys.utils.ReflectUtil;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.DefaultStateRevert;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
// CHECKSTYLE: MagicNumber OFF
@SuppressWarnings({ "PMD.AssignmentInOperand", "PMD.AvoidUsingVolatile", "PMD.CompareObjectsWithEquals", "PMD.DoNotUseThreads", "checkstyle:MagicNumber", "PMD.OptimizableToArrayCall" })
@Component
@Slf4j
public class ContextSnapshotControllerImpl implements ContextSnapshotFactory, ContextSnapshotLifecycleListenerExtendable, ContextSnapshotController, InitializingBean, DisposableBean {
    public static final Object[] ALL_NULL_VALUES = new Object[0];

    public static final StateRevert[] ALL_NULL_REVERTS = new StateRevert[0];

    // this constant is intentionally different from the ABOVE. do not refactor this
    public static final Object[] EMPTY_VALUES = new Object[0];

    public static final TransferrableThreadLocal<?>[] EMPTY_THREAD_LOCALS = new TransferrableThreadLocal[0];

    public static final Field[] EMPTY_FIELDS = new Field[0];

    protected final ThreadLocal<List<PushedContext>> pushedContextsTL = new NamedThreadLocal<>("ContextSnapshotFactory.pushedContextsTL");

    protected volatile int monitoredThreadLocalCount;

    protected final List<ContextSnapshotLifecycleListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    protected ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    @Autowired
    protected ReflectUtil reflectUtil;

    @Autowired
    protected SneakyThrowUtil sneakyThrowUtil;

    @Autowired
    protected ThreadLocalTransferrerRegistry threadLocalTransferrerRegistry;

    @Autowired(required = false)
    protected TransferrableThreadScopeIntern transferrableThreadScope;

    @Autowired
    protected TransferrableThreadLocalPostProcessor transferrableThreadLocalPostProcessor;

    @Autowired
    protected TransferrableThreadLocals transferrableThreadLocals;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (transferrableThreadScope == null) {
            log.info("No {} defined. Falling back to no-op implementation", TransferrableThreadScopeIntern.class.getSimpleName());
            transferrableThreadScope = new TransferrableThreadScopeIntern() {
                @Override
                public Map<String, Object> getThreadScopeMap() {
                    return null;
                }

                @Override
                public Map<String, Object> getAndRemoveThreadScopeMap() {
                    return null;
                }

                @Override
                public void setThreadScopeMap(Map<String, Object> threadScopeMap) {
                    // intended blank
                }

                @Override
                public TransferrableBeanProcessor getBeanProcessor() {
                    return null;
                }

                @Override
                public void setBeanPostProcessor(TransferrableBeanProcessor beanProcessor) {
                    // intended blank
                }
            };
        }
    }

    @Override
    public void destroy() throws Exception {
        pushedContextsTL.remove();
    }

    @Override
    public <T> Stream<T> parallel(Stream<T> stream) {
        if (stream == null) {
            return Stream.empty();
        }
        return threadlyStreamingConfiguration.isParallelActive() ? stream.parallel() : stream.sequential();
    }

    @Override
    public <T> Stream<T> parallel(Collection<T> collection) {
        if (collection == null) {
            return Stream.empty();
        }
        if (collection.size() <= 1) {
            return collection.stream();
        }
        return threadlyStreamingConfiguration.isParallelActive() ? collection.parallelStream() : collection.stream();
    }

    @Override
    public ContextSnapshot createSnapshot() {
        var threadLocals = transferrableThreadLocalPostProcessor.getOrCreateCachedThreadLocals();
        var threadScopeMap = transferrableThreadScope.getThreadScopeMap();
        if (threadScopeMap == null) {
            threadScopeMap = Collections.emptyMap();
        }
        if (threadLocals == null) {
            threadLocals = EMPTY_THREAD_LOCALS;
        }

        var clonedThreadScopeMap = createSnapshotForScopedBeans(threadScopeMap);
        addUnitializedScopedBeans(clonedThreadScopeMap);
        var values = createSnapshotForThreadLocals(threadLocals);

        var cs = new ContextSnapshotImpl(this, this, threadLocalTransferrerRegistry, reflectUtil, sneakyThrowUtil, threadLocals, values, clonedThreadScopeMap);

        log.debug("Context snapshot created: {}", cs);
        listeners.stream()
                .forEach(listener -> listener.contextSnapshotCreated(cs));
        return cs;
    }

    protected Map<String, ThreadScopeEntry> createSnapshotForScopedBeans(Map<String, Object> threadScopeMap) {
        var clonedThreadScopeMap = new HashMap<String, ThreadScopeEntry>((int) (threadScopeMap.size() / 0.75f) + 1);

        threadScopeMap.entrySet()
                .forEach(entry -> {
                    var originalBean = entry.getValue();
                    var allFields = reflectUtil.getAllDeclaredFields(originalBean.getClass());
                    var fieldsList = new ArrayList<>();
                    for (Field field : allFields) {
                        if (field.getAnnotation(Transferrable.class) == null) {
                            continue;
                        }
                        fieldsList.add(field);
                    }
                    var fieldValues = ALL_NULL_VALUES;
                    var fields = EMPTY_FIELDS;

                    if (!fieldsList.isEmpty()) {
                        fields = fieldsList.toArray(new Field[fieldsList.size()]);
                        fieldValues = new Object[fields.length];
                        for (int a = fields.length; a-- > 0;) {
                            fieldValues[a] = reflectUtil.getFieldValue(fields[a], originalBean);
                        }
                    }
                    clonedThreadScopeMap.put(entry.getKey(), new ThreadScopeEntry(originalBean, fields, fieldValues));
                });
        return clonedThreadScopeMap;
    }

    protected void addUnitializedScopedBeans(Map<String, ThreadScopeEntry> clonedThreadScopeMap) {
        var pushedContexts = pushedContextsTL.get();
        if (pushedContexts != null && !pushedContexts.isEmpty()) {
            // make sure that we do not forget all uninitialized thread-local
            // beans from a parent context snapshot
            PushedContext pushedContext = pushedContexts.get(pushedContexts.size() - 1);
            Map<String, ThreadScopeEntry> parentThreadScopeMap = pushedContext.getContextSnapshot()
                    .getThreadScopeMap();
            parentThreadScopeMap.entrySet()
                    .forEach(entry -> clonedThreadScopeMap.putIfAbsent(entry.getKey(), entry.getValue()));
        }
    }

    protected Object[] createSnapshotForThreadLocals(TransferrableThreadLocal<?>[] threadLocals) {
        if (threadLocals.length == 0) {
            return EMPTY_VALUES;
        }
        var values = new Object[threadLocals.length];
        for (int a = threadLocals.length; a-- > 0;) {
            values[a] = threadLocals[a].get();
        }
        return values;
    }

    @Override
    public StateRevert pushContext(ContextSnapshotIntern cs) {
        var thread = Thread.currentThread();
        log.debug("identity=CS{} - context snapshot APPLY  START  on thread '{}-{}': {}", System.identityHashCode(cs), thread.getId(), thread.getName(), cs);
        var threadLocals = cs.getThreadLocals();
        var values = cs.getValues();

        var oldValueReverts = ALL_NULL_REVERTS;
        for (int a = threadLocals.length; a-- > 0;) {
            @SuppressWarnings("unchecked")
            TransferrableThreadLocal<Object> threadLocal = (TransferrableThreadLocal<Object>) threadLocals[a];
            Object oldValue = threadLocal.get();
            Object newValue = values[a];
            var revert = threadLocal.setForFork(newValue, oldValue);
            if (revert == null || revert == DefaultStateRevert.empty()) {
                // no old value to store
                continue;
            }
            if (oldValueReverts == ALL_NULL_REVERTS) {
                // once: now the full lazy instantiation of the oldValues storage
                oldValueReverts = new StateRevert[threadLocals.length];
            }
            oldValueReverts[a] = revert;
        }
        var oldThreadScopeMap = transferrableThreadScope.getAndRemoveThreadScopeMap();
        var oldBeanProcessor = transferrableThreadScope.getBeanProcessor();

        transferrableThreadScope.setBeanPostProcessor(cs);

        var pushedContexts = pushedContextsTL.get();
        if (pushedContexts == null) {
            pushedContexts = new ArrayList<>();
            pushedContextsTL.set(pushedContexts);
        }
        pushedContexts.add(new PushedContext(cs, oldValueReverts, oldThreadScopeMap, oldBeanProcessor));

        log.debug("identity=CS{} - context snapshot APPLY  FINISH on thread '{}-{}': {}", System.identityHashCode(cs), thread.getId(), thread.getName(), cs);
        listeners.stream()
                .forEach(listener -> listener.contextSnapshotApplied(cs));

        return () -> popContext(cs);
    }

    @Override
    public void popContext(ContextSnapshotIntern cs) {
        var thread = Thread.currentThread();
        log.debug("identity=CS{} - context snapshot REVERT START  on thread '{}-{}': {}", System.identityHashCode(cs), thread.getId(), thread.getName(), cs);
        var pushedContexts = pushedContextsTL.get();
        if (pushedContexts == null || pushedContexts.isEmpty()) {
            throw new IllegalStateException("No prior invocation to pushContext() resolved from this thread");
        }
        var recentContext = popRecentContextIfValid(pushedContexts, cs);
        if (pushedContexts.isEmpty()) {
            pushedContextsTL.remove();
        }
        transferrableThreadScope.setBeanPostProcessor(recentContext.getOldBeanProcessor());

        var threadScopeMap = transferrableThreadScope.getThreadScopeMap();
        var oldThreadScopeMap = recentContext.getOldThreadScopeMap();

        transferrableThreadLocalPostProcessor.disposeAllForkedThreadLocalBeans(threadScopeMap, oldThreadScopeMap);
        transferrableThreadScope.setThreadScopeMap(oldThreadScopeMap);

        applyValuesToThreadLocals(recentContext.getOldValues(), cs.getThreadLocals());

        log.debug("identity=CS{} - context snapshot REVERT FINISH on thread '{}-{}': {}", System.identityHashCode(cs), thread.getId(), thread.getName(), cs);
        listeners.stream()
                .forEach(listener -> listener.contextSnapshotReverted(cs));
    }

    private PushedContext popRecentContextIfValid(List<PushedContext> pushedContexts, ContextSnapshotIntern expectedContextSnapshot) {
        var lastIndex = pushedContexts.size() - 1;
        var recentContext = pushedContexts.get(lastIndex);
        var cs = recentContext.getContextSnapshot();
        if (cs != expectedContextSnapshot) {
            throw new IllegalStateException("Recent context (" + cs + ") does not match provided instance (" + expectedContextSnapshot
                    + "). Seems your push() operations dont match symmetrically to pop() operations within the same thread");
        }
        pushedContexts.remove(lastIndex);
        return recentContext;
    }

    protected void applyValuesToThreadLocals(StateRevert[] oldValues, TransferrableThreadLocal<?>[] threadLocals) {
        if (oldValues == ALL_NULL_REVERTS) {
            return;
        }
        for (int a = oldValues.length; a-- > 0;) {
            var oldValue = oldValues[a];
            if (oldValue != null) {
                oldValue.revert();
            }
        }
    }

    @Override
    public ContextSnapshot currentSnapshot() {
        var pushedContexts = pushedContextsTL.get();
        if (pushedContexts == null || pushedContexts.isEmpty()) {
            return emptySnapshot();
        }
        return pushedContexts.get(pushedContexts.size() - 1)
                .getContextSnapshot();
    }

    @Override
    public ContextSnapshot emptySnapshot() {
        return new NoOpContextSnapshot(this, sneakyThrowUtil);
    }

    @Override
    public StateRevert registerContextSnapshotLifecycleListener(ContextSnapshotLifecycleListener listener) {
        ListenersListAdapter.registerListener(listener, listeners);
        return () -> unregisterContextSnapshotLifecycleListener(listener);
    }

    protected void unregisterContextSnapshotLifecycleListener(ContextSnapshotLifecycleListener listener) {
        ListenersListAdapter.unregisterListener(listener, listeners);
    }
}
