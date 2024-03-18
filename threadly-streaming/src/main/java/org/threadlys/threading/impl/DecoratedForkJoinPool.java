package org.threadlys.threading.impl;

import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.StateRollback;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class DecoratedForkJoinPool extends ForkJoinPool {

    private static final float MAP_LOAD_FACTOR = 0.5f;

    @RequiredArgsConstructor
    public static class DecoratedRunnable implements Runnable {
        @Getter
        final DecoratedForkJoinPool forkJoinPool;

        @Getter
        final Runnable runnable;

        @Override
        public void run() {
            var rollback = forkJoinPool.pushMonitorCurrentThread();
            try {
                runnable.run();
            } finally {
                rollback.rollback();
            }
        }
    }

    @RequiredArgsConstructor
    public static class DecoratedCallable<T> implements Callable<T> {
        @Getter
        final DecoratedForkJoinPool forkJoinPool;

        @Getter
        final Callable<T> callable;

        @Override
        public T call() throws Exception {
            var rollback = forkJoinPool.pushMonitorCurrentThread();
            try {
                return callable.call();
            } finally {
                rollback.rollback();
            }
        }
    }

    public static class ReentrantValue {

        @Getter
        Instant validUntil;

        @Getter
        int counter;

        void incrementCounter(Instant refreshedValidUntil) {
            validUntil = refreshedValidUntil;
            counter++;
        }

        void decrementCounter() {
            counter--;
        }
    }

    protected final Map<Thread, ReentrantValue> threadToReentrantCounterMap = new ConcurrentHashMap<>((int) (16 / MAP_LOAD_FACTOR) + 1, MAP_LOAD_FACTOR);

    protected final List<DecoratedForkJoinPoolListener> listeners = new CopyOnWriteArrayList<>();

    protected final Duration workerTimeout;

    public DecoratedForkJoinPool(Duration workerTimeout, int parallelism, ForkJoinWorkerThreadFactory factory, UncaughtExceptionHandler handler, boolean asyncMode, int corePoolSize,
            int maximumPoolSize, int minimumRunnable, Predicate<? super ForkJoinPool> saturate, long keepAliveTime, TimeUnit unit) {
        super(parallelism, factory, handler, asyncMode, corePoolSize, maximumPoolSize, minimumRunnable, saturate, keepAliveTime, unit);
        this.workerTimeout = workerTimeout;
    }

    public IStateRollback registerListener(DecoratedForkJoinPoolListener listener) {
        Objects.requireNonNull(listener, "listener must be valid");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public Collection<Thread> resolveBusyThreads() {
        return List.copyOf(threadToReentrantCounterMap.keySet());
    }

    public Collection<Thread> resolveBusyTimedOutThreads() {
        if (threadToReentrantCounterMap.isEmpty()) {
            return List.of();
        }
        var validUntil = Instant.now();
        return threadToReentrantCounterMap.entrySet()
                .stream()
                .map(entry -> entry.getValue()
                        .getValidUntil()
                        .isBefore(validUntil) ? entry.getKey() : null)
                .filter(Objects::nonNull)
                .toList();
    }

    protected IStateRollback pushMonitorCurrentThread() {
        if (workerTimeout == null && listeners.isEmpty()) {
            // no monitoring at all
            return StateRollback.empty();
        }
        var thread = Thread.currentThread();
        threadToReentrantCounterMap.compute(thread, (key, reentrantCounter) -> {
            if (reentrantCounter == null) {
                reentrantCounter = new ReentrantValue();
            }
            reentrantCounter.incrementCounter(workerTimeout != null ? Instant.now()
                    .plus(workerTimeout) : Instant.MAX);
            return reentrantCounter;
        });
        if (!listeners.isEmpty()) {
            listeners.forEach(listener -> listener.threadPushed(this, thread));
        }
        return () -> {
            threadToReentrantCounterMap.compute(thread, (key, reentrantCounter) -> {
                if (reentrantCounter == null || reentrantCounter.getCounter() <= 1) {
                    // could happen in error cases where the thread referent is already cleaned up and the key lookup therefore not successful anymore
                    // also may happen in erroneus handling of the returned rollback handle
                    return null;
                }
                reentrantCounter.decrementCounter();
                return reentrantCounter;
            });
            if (!listeners.isEmpty()) {
                listeners.forEach(listener -> listener.threadPopped(this, thread));
            }
        };
    }

    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        throw new UnsupportedOperationException("Tasks can not be decorated at this moment");
    }

    @Override
    public void execute(ForkJoinTask<?> task) {
        throw new UnsupportedOperationException("Tasks can not be decorated at this moment");
    }

    @Override
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        throw new UnsupportedOperationException("Tasks can not be decorated at this moment");
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task must be valid");
        super.execute(decorate(task));
    }

    protected <T> Callable<T> decorate(Callable<T> task) {
        if (task instanceof DecoratedCallable) {
            return task;
        }
        return new DecoratedCallable<T>(this, task);
    }

    protected Runnable decorate(Runnable task) {
        if (task instanceof DecoratedRunnable) {
            return task;
        }
        return new DecoratedRunnable(this, task);
    }

    protected <T> Collection<? extends Callable<T>> decorate(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(this::decorate)
                .toList();
    }

    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task must be valid");
        return super.submit(decorate(task));
    }

    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return super.submit(decorate(task), result);
    }

    @Override
    public ForkJoinTask<?> submit(Runnable task) {
        return super.submit(decorate(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        return super.invokeAll(decorate(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return super.invokeAll(decorate(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return super.invokeAny(decorate(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return super.invokeAny(decorate(tasks), timeout, unit);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return super.newTaskFor(decorate(runnable), value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return super.newTaskFor(decorate(callable));
    }
}
