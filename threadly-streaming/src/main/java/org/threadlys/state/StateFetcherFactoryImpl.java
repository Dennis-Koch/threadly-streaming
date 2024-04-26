package org.threadlys.state;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ContextSnapshotFactory;

//CHECKSTYLE: DeclarationOrderCheck OFF
@Component
public class StateFetcherFactoryImpl implements StateFetcherFactory, DisposableBean {

    private static final Map<Thread, AtomicInteger> INVOKED_BY_MAP = new WeakHashMap<>();

    private static String acquireThreadPoolName() {
        Thread currentThread = Thread.currentThread();
        AtomicInteger seq;
        synchronized (INVOKED_BY_MAP) {
            seq = INVOKED_BY_MAP.computeIfAbsent(currentThread, key -> new AtomicInteger());
        }
        var threadingPrefix = System.getProperty("threading.prefix");
        if (threadingPrefix == null || threadingPrefix.isEmpty()) {
            return currentThread.getName() + "-SF-" + seq.incrementAndGet();
        }
        return currentThread.getName() + threadingPrefix + "-SF-" + seq.incrementAndGet();
    }

    /**
     * This supplier creates a threadfactory that assigns the name of the CURRENT thread as a prefix to all threads created by this factory and therefore all threads used by the corresponding
     * threadpool. This is especially helpful for debugging & logging as you can "semantically connect" log statements of worker threads together and with their master thread
     */
    public static final Function<AtomicInteger, ThreadFactory> THREAD_FACTORY_SUPPLIER = seq -> new ThreadFactory() {
        String threadPoolName = acquireThreadPoolName();

        @Override
        public Thread newThread(Runnable r) {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName(threadPoolName + "-" + seq.incrementAndGet());
            return t;
        }
    };

    AtomicInteger seq = new AtomicInteger();

    protected ExecutorService executorService = Executors.newCachedThreadPool(THREAD_FACTORY_SUPPLIER.apply(seq));

    @Autowired
    protected ContextSnapshotFactory contextSnapshotFactory;

    @Autowired
    protected StateFetcherFactoryInternal stateFetcherFactoryInternal;

    protected final StateFetcherExecutor taskExecutor = new StateFetcherExecutor() {
        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return executorService.submit(() -> executeCallableAndCleanupWorkerState(task));
        }
    };

    protected <T> T executeCallableAndCleanupWorkerState(Callable<T> task) throws Exception {
        var cs = contextSnapshotFactory.createSnapshot();
        var rollback = cs.apply();
        try {
            return task.call();
        } finally {
            rollback.rollback();
        }
    }

    @Override
    public void destroy() throws Exception {
        executorService.shutdownNow();
    }

    @Override
    public <S> StateFetcher<S> createStateFetcher(Supplier<S> stateSupplier) {
        return StateFetcherImpl.<S>builder()
                .taskExecutor(taskExecutor)
                .stateSupplier(stateSupplier)
                .build();
    }

    @Override
    public <S> StateFetcher<S> createStateFetcher(Supplier<S> stateSupplier, Consumer<S> oldStateProcessor) {
        return StateFetcherImpl.<S>builder()
                .taskExecutor(taskExecutor)
                .oldStateProcessor(oldStateProcessor)
                .stateSupplier(stateSupplier)
                .build();
    }
}
