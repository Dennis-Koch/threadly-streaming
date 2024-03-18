package org.threadlys.streams;

import java.util.function.Predicate;

/**
 * A {@link Predicate}-like interface which allows throwing checked exceptions.
 */
@FunctionalInterface
public interface CheckedPredicate<T> {
    boolean test(T t) throws Exception;
}
