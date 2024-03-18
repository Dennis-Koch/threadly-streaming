package org.threadlys.threading;

import org.threadlys.streams.CheckedFunction;
import org.springframework.stereotype.Component;

/**
 * Providing methods to create instances of {@link TransferrableThreadLocal}.
 *
 * @author Dennis Koch (EXXETA AG)
 */
@Component
public interface TransferrableThreadLocals {
    <T> TransferrableThreadLocal<T> wrap(ThreadLocal<T> threadLocal);

    <T> TransferrableThreadLocal<T> wrap(ThreadLocal<T> threadLocal, CheckedFunction<T, T> valueCloner);
}
