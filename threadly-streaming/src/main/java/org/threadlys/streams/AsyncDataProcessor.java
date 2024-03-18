package org.threadlys.streams;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The AsyncDataProcessor encapsulates the logic how to fork/join async operations on configured {@link DataProcessor} instances
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface AsyncDataProcessor {
    /**
     * Resolves all via {@link DataProcessorExtendable} configured {@link DataProcessor} instance of the given entityType. It then filters all those data processors for the ones that apply to the
     * given list of data scopes. On the resulting list of applicable data processors it invokes them asynchronously and reduces the results back into the given list of entities.<br>
     * <br>
     * As a result the given entities have enriched properties fetched concurrently to reduce latency.
     *
     * @param <E>
     * @param <C>
     * @param entityType
     * @param entityList
     * @param dataScopes
     * @param contextBuilder
     * @param entityToUsedDataScopes
     */
    <E, C extends DataProcessorContext> void processAllEntities(Class<E> entityType, Collection<?> entityList, Collection<DataScope> dataScopes, Function<E, C> contextBuilder,
            Map<Object, Map<Object, Set<DataScope>>> entityToUsedDataScopes);

    /**
     * Resolves all via {@link DataProcessorExtendable} configured {@link DataProcessor} instance of the given entityType. It then filters all those data processors for the ones that apply to the
     * given list of data scopes. On the resulting list of applicable data processors it invokes them asynchronously and reduces the results back into the given list of entities.<br>
     * <br>
     * As a result the given entities have enriched properties fetched concurrently to reduce latency.
     * 
     * @param <E>
     * @param <C>
     * @param entityType
     * @param entityList
     * @param dataScopeSupplier
     * @param contextBuilder
     * @param entityToUsedDataScopes
     */
    <E, C extends DataProcessorContext> void processAllEntities(Class<E> entityType, Collection<?> entityList, Function<E, Collection<DataScope>> dataScopeSupplier, Function<E, C> contextBuilder,
            Map<Object, Map<Object, Set<DataScope>>> entityToUsedDataScopes);
}
