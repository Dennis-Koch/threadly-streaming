package org.threadlys.threading;

import org.threadlys.streams.CheckedFunction;

/**
 * Allows to create instances of {@link ContextSnapshot} which contain the
 * values of all managed thread-local fields. Thread-local fields are managed if
 * beans either<br>
 * <ul>
 * <li>annotated them with {@link Transferrable}</li>
 * <li>or marked themselves as {@link TransferrableThreadLocalProvider} and
 * returning the said thread-local handles}</li>
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface ContextSnapshotFactory extends ParallelStreamFassade {
    /**
     * Creates a no-op snapshot handle (=mock). That is a handle that does not
     * change any thread-local state with any of its
     * {@link ContextSnapshot#scoped(CheckedFunction)}
     * overloads or its {@link ContextSnapshot#push()}/{@link ContextSnapshot#pop()
     * operations.
     *
     * @return A fresh instance of a no-op snapshot handle (=mock)
     */
    ContextSnapshot emptySnapshot();

    /**
     * Creates a new snapshot from all managed thread-local values and thread-local
     * beans in their state that this moment on time for the calling thread. The
     * resulting handle can then be passing to worker threads in order to apply this
     * captured thread-local state to them.
     *
     * @return A fresh instance of a snapshot containing all current values of all
     *         managed thread-local variables
     */
    ContextSnapshot createSnapshot();

    /**
     * Resolves the most recent snapshot handle that is applied to the current
     * thread. Snapshot handles are considered applied if there is one of the
     * following active on the stack:
     * <ul>
     * <li>{@link ContextSnapshot#apply(org.threadlys.utils.IStateRollback...)}</li>
     * <li>{@link ContextSnapshot#scoped(org.threadlys.streams.CheckedRunnable)}
     * or any other of the scoped() overloads</li>
     * <li>{@link ContextSnapshot#push()}</li>
     * </ul>
     *
     * @return The most recent snapshot handle applied to this thread. Returns null
     *         if none is applied
     */
    ContextSnapshot currentSnapshot();
}
