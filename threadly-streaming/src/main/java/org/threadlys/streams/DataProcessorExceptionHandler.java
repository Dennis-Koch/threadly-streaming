package org.threadlys.streams;

/**
 * Instances of this can be registered to {@link DataProcessorExtendable#registerDataProcessorExceptionHandler(DataProcessor, DataProcessorExceptionHandler)}
 *
 */
@FunctionalInterface
public interface DataProcessorExceptionHandler {

    /**
     * Method is invoked whenever the corresponding data processor throws an unhandled exception on {@link DataProcessor#process(Object)}. It allows to completely generate a fallback result value for
     * {@link CheckedConsumer} if required.
     * 
     * @param <E>
     * @param <C>
     * @param dataProcessor
     * @param context
     * @param e
     * @return
     */
    <E, C> CheckedConsumer<E> handleProcessException(DataProcessor<E, C> dataProcessor, C context, Throwable e);
}
