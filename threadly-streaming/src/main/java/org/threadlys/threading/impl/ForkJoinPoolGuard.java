package org.threadlys.threading.impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.threadlys.streams.CheckedRunnable;
import org.threadlys.utils.FutureUtil;
import org.threadlys.utils.IStateRevert;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.DefaultStateRevert;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.streams.CheckedSupplier;
import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;
import org.threadlys.threading.TransferrableThreadLocals;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Allows to have a custom ForkJoinPool instead of the VM-global common ForkJoinPool for being used e.g. by the Java Streams API via {@link Collection#parallelStream()} or by Spring with its Async
 * annotation on methods. If a web service running from a Tomcat thread pool makes use of <code>parallelStream()</code> without this annotation there is a high risk of leaking thread-local variables
 * across the boundaries a normal request-scope. Same can happen with any out-of-the-box ExecutorService.<br>
 * <br>
 * Running this guard ensures that thread-local variables are in any worst-case only leaked across the workers of the SAME web service request. But in most cases even the workers are initialized &
 * cleared properly on each of their task. Please see usage examples documented on {@link ContextSnapshot}
 *
 * @author Dennis Koch (EXXETA AG)
 */
// CHECKSTYLE: DeclarationOrderCheck OFF
// CHECKSTYLE: IllegalCatch OFF
// CHECKSTYLE: MagicNumber OFF
@Slf4j
@Component
@SuppressWarnings({ "checkstyle:DeclarationOrderCheck", "checkstyle:IllegalCatch", "checkstyle:MagicNumber", "PMD.DoNotUseThreads" })
public class ForkJoinPoolGuard implements TransferrableThreadLocalProvider, DisposableBean {

    private static final Map<Thread, AtomicInteger> INVOKED_BY_MAP = new WeakHashMap<>();

    private static String acquireThreadPoolName() {
        Thread currentThread = Thread.currentThread();
        AtomicInteger seq;
        synchronized (INVOKED_BY_MAP) {
            seq = INVOKED_BY_MAP.computeIfAbsent(currentThread, key -> new AtomicInteger());
        }
        var threadingPrefix = System.getProperty("threading.prefix");
        if (threadingPrefix == null || threadingPrefix.isEmpty()) {
            return currentThread.getName() + "-FJP-" + seq.incrementAndGet();
        }
        return currentThread.getName() + threadingPrefix + "-FJP-" + seq.incrementAndGet();
    }

    /**
     * This supplier creates a threadfactory that assigns the name of the CURRENT thread as a prefix to all threads created by this factory and therefore all threads used by the corresponding
     * threadpool. This is especially helpful for debugging & logging as you can "semantically connect" log statements of worker threads together and with their master thread
     */
    public static final Supplier<ForkJoinWorkerThreadFactory> THREAD_FACTORY_SUPPLIER = () -> new ForkJoinWorkerThreadFactory() {
        String threadPoolName = acquireThreadPoolName();

        AtomicInteger seq = new AtomicInteger();

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName(threadPoolName + "-" + seq.incrementAndGet());
            return worker;
        }
    };

    @Autowired
    ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    @Autowired
    ContextSnapshotFactory contextSnapshotFactory;

    @Autowired
    FutureUtil futureUtil;

    @Autowired
    SneakyThrowUtil sneakyThrowUtil;

    @Autowired
    TransferrableThreadLocals transferrableThreadLocals;

    @Autowired(required = false)
    List<DecoratedForkJoinPoolListener> forkJoinPoolListeners = List.of();

    ThreadLocal<ForkJoinPool> forkJoinPoolTL = new NamedThreadLocal<>("ForkJoinPoolGuard.forkJoinPoolTL");

    private final Object semaphore = new Object();

    private Optional<DecoratedForkJoinPool> defaultForkJoinPool = Optional.empty();

    private IStateRevert defaultListenerRevert = DefaultStateRevert.empty();

    @Override
    public void destroy() throws Exception {
        defaultListenerRevert.revert();
        defaultForkJoinPool.ifPresent(ForkJoinPool::shutdownNow);
    }

    public Optional<DecoratedForkJoinPool> getDefaultForkJoinPoolOptional() {
        return defaultForkJoinPool;
    }

    public DecoratedForkJoinPool getDefaultForkJoinPool() {
        var fjp = defaultForkJoinPool.orElse(null);
        if (fjp != null) {
            return fjp;
        }
        synchronized (semaphore) {
            fjp = defaultForkJoinPool.orElse(null);
            if (fjp != null) {
                return fjp;
            }
            var dfjp = createForkJoinPool();
            defaultListenerRevert = DefaultStateRevert.chain(chain -> {
                for (var listener : forkJoinPoolListeners) {
                    chain.append(dfjp.registerListener(listener));
                }
            });
            defaultForkJoinPool = Optional.of(dfjp);
            return dfjp;
        }
    }

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return List.of(transferrableThreadLocals.wrap(forkJoinPoolTL));
    }

    public ForkJoinPool currentForkJoinPool() {
        return forkJoinPoolTL.get();
    }

    public DecoratedForkJoinPool createForkJoinPool() {
        return new DecoratedForkJoinPool(threadlyStreamingConfiguration.getWorkerTimeout(), threadlyStreamingConfiguration.getPoolSize(), THREAD_FACTORY_SUPPLIER.get(), null, true,
                threadlyStreamingConfiguration.getPoolSize(), threadlyStreamingConfiguration.getMaximumPoolSize(), 1, fjp -> true, 10L, TimeUnit.SECONDS);
    }

    public IStateRevert pushForkJoinPool(ForkJoinPool fjp) {
        var existingFjp = forkJoinPoolTL.get();
        if (existingFjp == null && fjp == null) {
            // nothing to do
            return DefaultStateRevert.empty();
        }
        forkJoinPoolTL.set(fjp);
        return () -> {
            if (existingFjp != null) {
                forkJoinPoolTL.set(existingFjp);
            } else {
                forkJoinPoolTL.remove();
            }
        };
    }

    public IStateRevert pushForkJoinPoolIfRequired() {
        var existingFjp = forkJoinPoolTL.get();
        if (existingFjp != null) {
            // nothing to do
            return DefaultStateRevert.empty();
        }
        var fjp = createForkJoinPool();
        return DefaultStateRevert.chain(chain -> {
            chain.append(pushForkJoinPool(fjp));
            if (log.isDebugEnabled()) {
                log.debug("Concurrent processing enabled (" + threadlyStreamingConfiguration.getPoolSize() + " workers)");
            }
            chain.append(() -> {
                shutdownForkJoinPool(fjp);
                if (log.isDebugEnabled()) {
                    log.debug("Concurrent processing disabled");
                }
            });
        });
    }

    /**
     * Add the initialization of a custom ForkJoinPool on the current threads' stack in order to allow the use of parallel Stream API processing in
     *
     * @param joinPoint
     */
    public void reentrantInvokeOnForkJoinPool(CheckedRunnable joinPoint) {
        reentrantInvokeOnForkJoinPoolWithResult(() -> {
            joinPoint.run();
            return null;
        });
    }

    /**
     * Add the initialization of a custom ForkJoinPool on the current threads' stack in order to allow the use of parallel Stream API processing in
     *
     * @param <R>
     * @param joinPoint
     * @return
     */
    public <R> R reentrantInvokeOnForkJoinPoolWithResult(CheckedSupplier<R> joinPoint) {
        Throwable ex = null;
        var revert = pushForkJoinPoolIfRequired();
        try {
            ContextSnapshot cs = contextSnapshotFactory.createSnapshot();
            if (threadlyStreamingConfiguration.isParallelActive()) {
                ExecutorService forkJoinPool = forkJoinPoolTL.get();
                var waitTill = Instant.now()
                        .plus(threadlyStreamingConfiguration.getTimeout());
                var future = forkJoinPool.submit(() -> invokeJoinPoint(cs, joinPoint));
                return futureUtil.waitTill(future, waitTill);
            } else {
                // this is usable to make sure that a FJP is allocated and available via
                // currentForkJoinPool() for later usage.
                return invokeJoinPoint(cs, joinPoint);
            }
        } catch (Throwable e) {
            ex = sneakyThrowUtil.mergeStackTraceWithCause(e);
        } finally {
            revert.revert();
        }
        throw sneakyThrowUtil.sneakyThrow(ex);
    }

    /**
     * Tries to gracefully shutdown the given fork join pool
     *
     * @param fjp
     */
    public void shutdownForkJoinPool(ForkJoinPool fjp) {
        var gracePeriod = threadlyStreamingConfiguration.getGracePeriod();
        if (!gracePeriod.isNegative() && !gracePeriod.isZero()) {
            fjp.shutdown();
            try {
                fjp.awaitTermination(gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // clear interrupted flag
                Thread.interrupted();
            }
        }
        fjp.shutdownNow();
    }

    @SneakyThrows
    protected <R> R invokeJoinPoint(ContextSnapshot cs, CheckedSupplier<R> joinPoint) {
        var revert = cs != null ? cs.apply() : DefaultStateRevert.empty();
        try {
            return joinPoint.get();
        } finally {
            revert.revert();
        }
    }
}
