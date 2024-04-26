package org.threadlys.streams;

import java.util.function.Function;

/**
 * A {@link Function}-like interface which allows throwing checked exceptions.
 */
@FunctionalInterface
public interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
}
