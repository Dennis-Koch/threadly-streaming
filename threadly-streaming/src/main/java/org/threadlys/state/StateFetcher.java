package org.threadlys.state;

import java.util.concurrent.Future;

/**
 * Ensure that a given single state of something is provided at any point in time in a thread-safe manner. The value of said state may be expensive to resolve, so there are operations that allow to
 * pre-allocate it before required by the same thread or any other thread. As a result such state can be used when required ideally without a blocking the caller
 * 
 * @param <S>
 */
public interface StateFetcher<S> {

    /**
     * If the current state is undefined it queues activity which asynchronously resolves a defined state on this handle. As a result - and in ideal cases - the invocation of {@link #resolveState()}
     * by the current or any other thread at a later point in time will resolve this defined state and therefore return without blocking
     * 
     * @return The future handle encapsulating the ensured state. In case the current state is already defined this result is already a completed future.
     */
    Future<S> ensureStateAsync();

    /**
     * If the current state is defined it is immediately returned - in any other case it synchronously resolves a defined state and returns it. Ideally you want to make sure with a prior call to
     * {@link #ensureStateAsync()} that the given state is already defined. In order to force a fresh state resolution in any case you may invoke {@link #refreshStateAsync()} immediately before
     * invoking this method
     * 
     * @return The current state or a fresh resolved state
     */
    S resolveState();

    /**
     * Builds - with the current thread - a fresh defined state and replaces a potential current state afterwards
     * 
     * @param resetNow
     *            true if a current state shall be resetted immediately (in this thread). false if a potential existing defined state may be valid while the new state gets resolved (and on its
     *            resolution replace the existing state). Obviously this has an impact on concurrent threads and how they see this state refresh activity.
     * @return The fresh resolved state
     */
    S refreshState(boolean resetNow);

    /**
     * Resets any defined state and internally executes {@link #ensureStateAsync()} in order to resolve a fresh defined state
     * 
     * @param resetNow
     *            true if a current state shall be resetted immediately (in this thread). false if a potential existing defined state may be valid while the new state gets resolved (and on its
     *            resolution replace the existing state). Obviously this has an impact on concurrent threads and how they see this state refresh activity.
     */
    Future<S> refreshStateAsync(boolean resetNow);

    /**
     * If the current state is defined it get immediately invalidated. Any call to {@link #ensureStateAsync()} or {@link #resolveState()} or {@link #refreshStateAsync()} will initialize the state
     * again
     */
    void invalidateState();

    /**
     * Evaluates whether there is currently a defined state. Its mainly meant to be used for debugging or testing purposes
     * 
     * @return true if - and only if - at the given moment in time this holds a defined state. Note that even if this method returns true there is no guarantee that a consecutive call to
     *         {@link #resolveState()} will resolve that same prior existing state in an unblocked manner: E.g. there may be a concurrent call to {@link #refreshStateAsync()} with
     *         <code>resetNow=true</code> that has no finished, yet
     */
    boolean hasDefinedState();
}
