package org.threadlys.threading;

import org.threadlys.threading.impl.ContextSnapshotController;
import org.threadlys.utils.StateRevert;

/**
 * Encapsulates the logic to read and write from a thread-local handle. We don't use a thread-local handle directly in order to allow via fassade-pattern also more customizable logic in order to
 * resolve the thread-local handle dynamically (e.g. at invocation time vs. resolution time). Instances of this interface are normally created via methods of a {@link TransferrableThreadLocals} bean
 *
 * @author Dennis Koch (EXXETA AG)
 *
 * @param <T>
 */
public interface TransferrableThreadLocal<T> {
    /**
     * Called from the master thread in order to create a snapshot of all thread-locals while executing {@link ContextSnapshotFactory#createSnapshot()}. In addition this method is also called by
     * forked threads while executing {@link ContextSnapshotController#pushContext(org.threadlys.threading.impl.ContextSnapshotImpl, org.threadlys.utils.StateRevert...)}
     * in order to be able to restore the previous state of the worker.
     *
     * @return
     */
    T get();

    /**
     * Meant to be executed by a forked thread only. This is in order to apply the given snapshotted value to its thread-local state.
     *
     * @param value
     *            The thread-local value that shall be applied to the current thread for the given handle
     */
    StateRevert setForFork(T newForkedValue, T oldForkedValue);
}
