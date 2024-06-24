package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.threadlys.streams.CheckedRunnable;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.configuration.CommonsUtilsSpringConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import org.threadlys.configuration.CommonsThreadingAutoConfiguration;
import org.threadlys.configuration.CommonsThreadingSpringConfig;
import org.threadlys.threading.impl.DecoratedForkJoinPool;
import org.threadlys.threading.impl.DecoratedForkJoinPoolListener;
import org.threadlys.threading.impl.ForkJoinPoolGuard;

import lombok.SneakyThrows;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = { "threadlys.threading.filter-active=true", "threadlys.threading.task-executor-active=true", "threadlys.threading.timeout=#{T(java.time.Duration).parse(\"PT10M\")}",
        "threadlys.threading.gracePeriod=#{T(java.time.Duration).parse(\"PT0.5S\")}", "threadlys.threading.header-permitted=true", "threadlys.threading.transferrable-scope-active=true" })
public class DecoratedForkJoinPoolTest {

    @Configuration
    @Import({ CommonsThreadingSpringConfig.class, CommonsUtilsSpringConfig.class, CommonsThreadingAutoConfiguration.class })
    static class ContextConfiguration {
    }

    @Autowired
    ForkJoinPoolGuard forkJoinPoolGuard;

    @Autowired
    SneakyThrowUtil sneakyThrowUtil;

    @Test
    public void GIVEN_forkJoinTask_WHEN_calling_execute_THEN_throws_not_supported() {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
        assertThrows(UnsupportedOperationException.class, () -> fjp.execute((ForkJoinTask<Void>) null));
    }

    @Test
    public void GIVEN_forkJoinTask_WHEN_calling_submit_THEN_throws_not_supported() {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
        assertThrows(UnsupportedOperationException.class, () -> fjp.submit((ForkJoinTask<Void>) null));
    }

    @Test
    public void GIVEN_forkJoinTask_WHEN_calling_invoke_THEN_throws_not_supported() {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
        assertThrows(UnsupportedOperationException.class, () -> fjp.invoke((ForkJoinTask<Void>) null));
    }

    @Test
    public void GIVEN_runnable_WHEN_executed_by_fjp_worker_THEN_busy_threads_properly_observed() throws Exception {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();

        var latch2 = new CountDownLatch(1);
        var latch3 = new CountDownLatch(1);
        var threadPopLatch = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var ex = new AtomicReference<Throwable>(null);

        var threadRef = new AtomicReference<Thread>(null);

        Collection<Thread> busyThreads;
        Thread monitoredThread;

        var registerRevert = fjp.registerListener(new DecoratedForkJoinPoolListener() {
            @Override
            public void threadPushed(DecoratedForkJoinPool forkJoinPool, Thread fjpThread) {
                assertThat(threadRef.get()).isNull();
                threadRef.set(fjpThread);
            }

            @Override
            public void threadPopped(DecoratedForkJoinPool forkJoinPool, Thread fjpThread) {
                assertThat(threadRef.get()).isSameAs(fjpThread);
                threadRef.set(null);
                threadPopLatch.countDown();
            }
        });
        try {
            fjp.execute(finishableRunnable(() -> {
                latch2.countDown();

                awaitLatchDefault(latch3);
            }, finish, ex));

            awaitLatchDefault(latch2);

            monitoredThread = threadRef.get();
            assertThat(monitoredThread).isNotNull();

            busyThreads = fjp.resolveBusyThreads();
            assertThat(busyThreads).hasSize(1);
            assertThat(monitoredThread).isSameAs(busyThreads.iterator()
                    .next());

            latch3.countDown();

            awaitSuccess(60, TimeUnit.SECONDS, finish, ex);

            awaitLatchDefault(threadPopLatch);
        } finally {
            registerRevert.revert();
        }

        // original collection unmodified
        assertThat(busyThreads).hasSize(1);
        assertThat(monitoredThread).isSameAs(busyThreads.iterator()
                .next());

        var busyThreads2 = fjp.resolveBusyThreads();
        assertThat(busyThreads2).isEmpty();
    }

    @Test
    public void GIVEN_runnable_WHEN_executed_by_parallel_stream_THEN_busy_threads_properly_observed() throws Exception {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();

        var threadPopLatch = new CountDownLatch(1);
        var finish = new CountDownLatch(2);
        var ex = new AtomicReference<Throwable>(null);

        var threadRef = new AtomicReference<Thread>(null);

        Collection<Thread> busyThreads;
        Thread monitoredThread;

        var registerRevert = fjp.registerListener(new DecoratedForkJoinPoolListener() {
            @Override
            public void threadPushed(DecoratedForkJoinPool forkJoinPool, Thread fjpThread) {
                assertThat(threadRef.get()).isNull();
                threadRef.set(fjpThread);
            }

            @Override
            public void threadPopped(DecoratedForkJoinPool forkJoinPool, Thread fjpThread) {
                assertThat(threadRef.get()).isSameAs(fjpThread);
                threadRef.set(null);
                threadPopLatch.countDown();
            }
        });
        try {
            var barrier = new CyclicBarrier(3);
            var barrier2 = new CyclicBarrier(3);

            fjp.execute(() -> {
                Stream.of(1, 2)
                        .parallel()
                        .forEach(item -> {
                            try {
                                barrier.await(60, TimeUnit.SECONDS);
                                barrier2.await(60, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                ex.set(e);
                            } finally {
                                finish.countDown();
                            }
                        });
            });

            barrier.await(60, TimeUnit.SECONDS);

            monitoredThread = threadRef.get();
            assertThat(monitoredThread).isNotNull();

            busyThreads = fjp.resolveBusyThreads();
            assertThat(busyThreads).hasSize(1);
            assertThat(monitoredThread).isSameAs(busyThreads.iterator()
                    .next());

            barrier2.await(60, TimeUnit.SECONDS);

            awaitSuccess(60, TimeUnit.SECONDS, finish, ex);

            awaitLatchDefault(threadPopLatch);
        } finally {
            registerRevert.revert();
        }

        // original collection unmodified
        assertThat(busyThreads).hasSize(1);
        assertThat(monitoredThread).isSameAs(busyThreads.iterator()
                .next());

        var busyThreads2 = fjp.resolveBusyThreads();
        assertThat(busyThreads2).isEmpty();
    }

    protected Runnable finishableRunnable(CheckedRunnable runnable, CountDownLatch finishLatch, AtomicReference<Throwable> exHandle) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                exHandle.set(e);
            } finally {
                finishLatch.countDown();
            }
        };
    }

    protected void awaitLatchDefault(CountDownLatch latch) {
        awaitSuccess(60, TimeUnit.SECONDS, latch, null);
    }

    @SneakyThrows
    protected void awaitSuccess(long timeout, TimeUnit timeUnit, CountDownLatch finishLatch, AtomicReference<Throwable> exHandle) {
        if (!finishLatch.await(timeout, timeUnit)) {
            throw new TimeoutException();
        }
        if (exHandle != null && exHandle.get() != null) {
            throw exHandle.get();
        }
    }
}
