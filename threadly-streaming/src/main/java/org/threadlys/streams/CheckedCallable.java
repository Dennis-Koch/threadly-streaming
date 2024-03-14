package org.threadlys.streams;

import java.util.concurrent.Callable;

/**
 * A {@link Callable}-like interface which allows throwing checked exceptions.
 */
@FunctionalInterface
public interface CheckedCallable<R> {
    R call() throws Exception;
}
