package org.threadlys.threading.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;

import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.ListenersListAdapter;
import org.threadlys.utils.StateRollback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.threading.TaskExecutorListener;
import org.threadlys.threading.TaskExecutorListenerExtendable;

import lombok.extern.slf4j.Slf4j;

/**
 * This bean goes together with {@link Async}-annotated bean methods and the activation of that spring feature with a corresponding {@link EnableAsync}-annotated configuration. In such a scenario this
 * component delegates the async execution to an instance of a thread-local {@link ForkJoinPool}
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
// CHECKSTYLE: IllegalCatch OFF
@SuppressWarnings({ "checkstyle:IllegalCatch" })
@Component("taskExecutor")
@Slf4j
@ConditionalOnProperty(name = "threadlys.threading.task-executor-active", havingValue = "true", matchIfMissing = false)
public class ForkJoinPoolTaskExecutor implements TaskExecutor, TaskExecutorListenerExtendable {
    @Autowired
    ContextSnapshotFactory contextSnapshotFactory;

    @Autowired
    ForkJoinPoolGuard forkJoinPoolGuard;

    protected final List<TaskExecutorListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired
    private ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    @Override
    public void execute(Runnable command) {
        if (!threadlyStreamingConfiguration.isTaskExecutorActive()) {
            // no async execution active
            command.run();
            return;
        }
        var rollback = StateRollback.empty();
        try {
            var fjp = forkJoinPoolGuard.currentForkJoinPool();
            if (fjp == null) {
                fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
                rollback = forkJoinPoolGuard.pushForkJoinPool(fjp);
            }
            var cs = contextSnapshotFactory.createSnapshot();
            if (!log.isDebugEnabled() && listeners.isEmpty()) {
                // fast path
                fjp.execute(cs.scoped(command::run));
                return;
            }
            log.debug("Queueing @Async command {}", System.identityHashCode(command));

            fjp.execute(cs.scoped(() -> {
                log.debug("Processing @Async command {}", System.identityHashCode(command));
                try {
                    command.run();
                    listeners.forEach(listener -> listener.taskExecuted(command));
                } catch (Throwable e) {
                    listeners.forEach(listener -> {
                        try {
                            listener.taskFailed(command, e);
                        } catch (Throwable ex) {
                            log.error(e.getMessage(), ex);
                        }
                    });
                    throw e;
                }
                log.debug("Processed @Async command {}", System.identityHashCode(command));
            }));
            listeners.forEach(listener -> listener.taskQueued(command));
        } finally {
            rollback.rollback();
        }
    }

    @Override
    public IStateRollback registerTaskExecutorListener(TaskExecutorListener listener) {
        ListenersListAdapter.registerListener(listener, listeners);
        return () -> unregisterTaskExecutorListener(listener);
    }

    protected void unregisterTaskExecutorListener(TaskExecutorListener listener) {
        ListenersListAdapter.unregisterListener(listener, listeners);
    }
}
