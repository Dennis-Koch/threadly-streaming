package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.threadlys.streams.CheckedRunnable;
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
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.threading.impl.ForkJoinPoolWorkerTimeoutController;

import lombok.SneakyThrows;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = { "threadlys.threading.filter-active=true", "threadlys.threading.task-executor-active=true", "threadlys.threading.timeout=#{T(java.time.Duration).parse(\"PT10M\")}",
        "threadlys.threading.gracePeriod=#{T(java.time.Duration).parse(\"PT0.5S\")}", "threadlys.threading.header-permitted=true", "threadlys.threading.transferrable-scope-active=true",
        "threadlys.threading.worker-timeout=PT0.5S" })
public class ForkJoinPoolWorkerTimeoutControllerTest {

    @Configuration
    @Import({ CommonsThreadingSpringConfig.class, CommonsUtilsSpringConfig.class, CommonsThreadingAutoConfiguration.class })
    static class ContextConfiguration {
    }

    @Autowired
    ForkJoinPoolGuard forkJoinPoolGuard;

    @Autowired
    ForkJoinPoolWorkerTimeoutController fixture;

    @Test
    public void GIVEN_stale_fjp_thread_WHEN_timeout_reached_THEN_thread_interrupted() throws Exception {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();

        var latch1 = new CountDownLatch(1);
        var latch2 = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var ex = new AtomicReference<Throwable>(null);

        fjp.execute(finishableRunnable(() -> {
            latch1.countDown();

            // wait for latch that is never notified to force interruption
            awaitLatchDefault(latch2);
        }, finish, ex));

        awaitLatchDefault(latch1);

        // wait for the interval defined as worker timeout
        Thread.sleep(500);

        assertThat(fjp.resolveBusyThreads()).isNotEmpty();
        assertThat(fjp.resolveBusyTimedOutThreads()).isNotEmpty();

        assertThat(fjp.getActiveThreadCount()).isEqualTo(1);
        assertThat(fjp.getRunningThreadCount()).isEqualTo(0);

        fixture.checkForStaleWorkers();

        assertThrows(InterruptedException.class, () -> awaitSuccess(60, TimeUnit.SECONDS, finish, ex));

        assertThat(ex.get()).isExactlyInstanceOf(InterruptedException.class);

        assertThat(fjp.resolveBusyThreads()).isEmpty();
        assertThat(fjp.getActiveThreadCount()).isZero();
        assertThat(fjp.getRunningThreadCount()).isZero();

        var latch3 = new CountDownLatch(1);
        var finish3 = new CountDownLatch(1);
        var ex3 = new AtomicReference<Throwable>();

        // confirm that the still listening FJP worker is still valid for reuse
        fjp.execute(finishableRunnable(() -> {
            latch3.countDown();
        }, finish3, ex3));

        awaitLatchDefault(latch3);
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
