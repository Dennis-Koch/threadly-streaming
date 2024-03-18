package org.threadlys.threading.impl;

import java.util.Map;

import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ThreadLocalScope;
import org.threadlys.threading.TransferrableThreadLocal;

/**
 * Internal interface for communicating between the implementation of
 * {@link ContextSnapshot} and {@link ContextSnapshotController}.
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface ContextSnapshotIntern extends ContextSnapshot, TransferrableBeanProcessor {
    /**
     * Provides the managed thread-local handles at the point in time of snapshot
     * creation
     *
     * @return An array of managed thread-local handles of this snapshot
     */
    TransferrableThreadLocal<?>[] getThreadLocals();

    /**
     * Provides the values of the managed thread-local handles from the thread that
     * invoked the creation of this snapshot and at the point in time of its
     * creation.<br>
     * <br>
     * These are the values that need to be applied to the same correlating
     * thread-local handles - but by each worker-thread himself
     *
     * @return An array of values the managed thread-local handles contained
     */
    Object[] getValues();

    /**
     * The Spring related data structure specifically for thread-scoped beans. That
     * is all beans that are annotated with {@link ThreadLocalScope}. Not that this
     * data structure here is a clone of the real underlying map with the state of
     * the underlying map at the point in time of snapshot creation.<br>
     * <br>
     * These are the beans that need to be cloned to the same correlating
     * threadScopeMap - but by each worker-thread himself
     *
     * @return A map containing instantiated scoped beans of the ThreadLocalScope
     */
    Map<String, ThreadScopeEntry> getThreadScopeMap();
}
