package org.threadlys.streams;

import java.util.function.Supplier;

/**
 * A {@link Supplier}-like interface which allows throwing checked exceptions.
 */
@FunctionalInterface
public interface CheckedSupplier<R> {
    R get() throws Exception;
}
