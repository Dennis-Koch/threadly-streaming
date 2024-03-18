package org.threadlys.state;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface StateFetcherFactory {

    /**
     * Creates a new <code>StateFetcher</code> that maintains the result <code>S</code> eventually resolved by invoking the given supplier
     * 
     * @param <S>
     *            The state to be maintained
     * @param stateSupplier
     *            The supplier providing the state. It may be invoked when calling any of the {@link StateFetcher} methods.
     * 
     * @return A new <code>StateFetcher</code> than can be assigned to an instance variable for multi-threaded usage
     */
    <S> StateFetcher<S> createStateFetcher(Supplier<S> stateSupplier);

    <S> StateFetcher<S> createStateFetcher(Supplier<S> stateSupplier, Consumer<S> oldStateProcessor);
}
