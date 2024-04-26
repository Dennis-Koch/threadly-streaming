package org.threadlys.streams;

/**
 * Encapsulates the logic of fetching data that enriches properties of corresponding entities. A single data processor may be executed by an arbitrary number of threads from an arbitrary number of
 * independent client requests. All stateful information should be stored in the given context handle.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 * @param <E>
 *            The supported entity type
 * @param <C>
 *            The supported context type
 */
public interface DataProcessor<E, C> {
    CheckedConsumer<E> process(C context) throws Exception;

    default boolean expectsExecution(E entity, C context) {
        return true;
    }
}
