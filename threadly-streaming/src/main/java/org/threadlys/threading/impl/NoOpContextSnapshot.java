package org.threadlys.threading.impl;

import java.util.Collection;
import java.util.concurrent.Callable;
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
import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.StateRollback;

import lombok.RequiredArgsConstructor;

//CHECKSTYLE: IllegalCatch OFF
@SuppressWarnings({ "checkstyle:IllegalCatch" })
@RequiredArgsConstructor
public class NoOpContextSnapshot implements ContextSnapshot {
    private final ParallelStreamFassade parallelStreamFassade;

    private final SneakyThrowUtil sneakyThrowUtil;

    @Override
    public <T> Callable<T> scopedCallable(CheckedCallable<T> callable) {
        return () -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

    @Override
    public <R> Supplier<R> scoped(CheckedSupplier<R> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

    @Override
    public <T, R> Function<T, R> scoped(CheckedFunction<T, R> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

    @Override
    public Runnable scoped(CheckedRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

    @Override
    public <T> Predicate<T> scopedPredicate(CheckedPredicate<T> predicate) {
        return t -> {
            try {
                return predicate.test(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

    @Override
    public <T> Consumer<T> scopedConsumer(CheckedConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        };
    }

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
        return t -> t;
    }

    @Override
    public <T> Function<T, T> pop() {
        return t -> t;
    }

    @Override
    public IStateRollback apply() {
        return StateRollback.empty();
    }

    @Override
    public IStateRollback apply(IStateRollback... rollbacks) {
        return StateRollback.all(rollbacks);
    }

    @Override
    public String toString() {
        return "NoOpContextSnapshot";
    }
}
