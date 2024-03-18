package org.threadlys.threading;

import org.springframework.core.task.TaskExecutor;

/**
 * Callback that allows to observe the runtime workload on the configured
 * {@link TaskExecutor}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface TaskExecutorListener {
    default void taskQueued(Runnable command) {
        // intended blank
    }

    default void taskExecuted(Runnable command) {
        // intended blank
    }

    default void taskFailed(Runnable command, Throwable e) {
        // intended blank
    }
}
