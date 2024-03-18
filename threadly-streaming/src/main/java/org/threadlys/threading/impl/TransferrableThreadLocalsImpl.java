package org.threadlys.threading.impl;

import org.threadlys.streams.CheckedFunction;
import org.threadlys.utils.IStateRollback;
import org.springframework.stereotype.Component;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocals;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * Providing methods to create instances of {@link TransferrableThreadLocal}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
public class TransferrableThreadLocalsImpl implements TransferrableThreadLocals {
    @RequiredArgsConstructor
    public static class DefaultTransferrableThreadLocal<T> implements TransferrableThreadLocal<T> {
        @NonNull
        private final ThreadLocal<T> threadLocal;

        @Override
        public T get() {
            return threadLocal.get();
        }

        @Override
        public IStateRollback setForFork(T newForkedValue, T oldForkedValue) {
            if (newForkedValue == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(newForkedValue);
            }
            if (oldForkedValue == null) {
                return () -> threadLocal.remove();
            }
            return () -> threadLocal.set(oldForkedValue);
        }

        @Override
        public String toString() {
            return "Transferrable-" + threadLocal.toString();
        }
    }

    @RequiredArgsConstructor
    public static class CloningTransferrableThreadLocal<T> implements TransferrableThreadLocal<T> {
        @NonNull
        private final ThreadLocal<T> threadLocal;

        @NonNull
        private final CheckedFunction<T, T> valueCloner;

        @Override
        public T get() {
            return threadLocal.get();
        }

        @SneakyThrows
        @Override
        public IStateRollback setForFork(T newForkedValue, T oldForkedValue) {
            var clonedValue = valueCloner.apply(newForkedValue);
            if (clonedValue == null) {
                threadLocal.remove();
            } else {
                threadLocal.set(clonedValue);
            }
            if (oldForkedValue == null) {
                return () -> threadLocal.remove();
            } else {
                return () -> threadLocal.set(oldForkedValue);
            }
        }

        @Override
        public String toString() {
            return "CloningTransferrable-" + threadLocal.toString();
        }
    }

    @Override
    public <T> TransferrableThreadLocal<T> wrap(ThreadLocal<T> threadLocal) {
        return new DefaultTransferrableThreadLocal<>(threadLocal);
    }

    @Override
    public <T> TransferrableThreadLocal<T> wrap(ThreadLocal<T> threadLocal, CheckedFunction<T, T> valueCloner) {
        return new CloningTransferrableThreadLocal<>(threadLocal, valueCloner);
    }
}
