package org.threadlys.threading;

/**
 * Callback that allows to observe the lifecycle of {@link ContextSnapshot}
 * instances
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface ContextSnapshotLifecycleListener {
    default void contextSnapshotCreated(ContextSnapshot contextSnapshot) {
        // intended blank
    }

    default void contextSnapshotApplied(ContextSnapshot contextSnapshot) {
        // intended blank
    }

    default void contextSnapshotReverted(ContextSnapshot contextSnapshot) {
        // intended blank
    }
}
