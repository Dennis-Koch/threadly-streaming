package org.threadlys.streams;

import java.util.Collection;

import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.StateRollback;

/**
 * Defines extension points for registering data processors in order to make them available within the {@link AsyncDataProcessor} engine
 */
public interface DataProcessorExtendable {
    default <E> IStateRollback registerDataProcessor(DataProcessor<E, ?> dataProcessor, Class<? extends E> entityType, Collection<DataScope> dataScopes, Collection<DataScope> requiredDataScopes) {
        return StateRollback.chain(chain -> {
            if (dataScopes != null) {
                for (DataScope dataScope : dataScopes) {
                    chain.append(registerDataProcessor(dataProcessor, entityType, dataScope));
                }
            }
            if (requiredDataScopes != null) {
                for (DataScope requiredDataScope : requiredDataScopes) {
                    chain.append(registerDataProcessorDependency(dataProcessor, requiredDataScope));
                }
            }
        });
    }

    /**
     * Registers a data processor for the given entity type and the given data scope of this entity type
     * 
     * @param <E>
     * @param dataProcessor
     * @param entityType
     * @param dataScope
     */
    <E> IStateRollback registerDataProcessor(DataProcessor<E, ?> dataProcessor, Class<? extends E> entityType, DataScope dataScope);

    /**
     * Registers a data processor to require the given data scope before executing this data processor. Normally the required data scope of maintained by another data processor. This way you can
     * enforce sequential step / pre-requisites in the parallel concept of data processors.
     * 
     * @param <E>
     * @param dataProcessor
     * @param requiredDataScope
     */
    <E> IStateRollback registerDataProcessorDependency(DataProcessor<E, ?> dataProcessor, DataScope requiredDataScope);

    /**
     * Registers a data processor to require the given exception handler for unhandled exceptions. Executions of {@link DataProcessor#process(Object)} will be enclosed with a try/catch and exceptions
     * get redirected to the given exception handler.
     * 
     * @param <E>
     * @param dataProcessor
     * @param exceptionHandler
     */
    <E> IStateRollback registerDataProcessorExceptionHandler(DataProcessor<E, ?> dataProcessor, DataProcessorExceptionHandler exceptionHandler);
}
