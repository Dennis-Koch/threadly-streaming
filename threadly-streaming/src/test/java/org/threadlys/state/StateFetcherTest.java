package org.threadlys.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.threadlys.utils.configuration.CommonsUtilsSpringConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.threadlys.configuration.CommonsThreadingSpringConfig;
import org.threadlys.threading.impl.ForkJoinPoolGuard;

import lombok.SneakyThrows;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@TestPropertySource(properties = "threadlys.threading.task-executor-active=true")
public class StateFetcherTest {
    @EnableAsync
    @Configuration
    @Import({ CommonsThreadingSpringConfig.class, CommonsUtilsSpringConfig.class })
    static class ContextConfiguration {
    }

    @Autowired
    ForkJoinPoolGuard forkJoinPoolGuard;

    @Autowired
    StateFetcherFactory stateFetcherFactory;

    @Test
    void testRefreshStateAsyncKeepCurrentState() {
        // GIVEN
        var value = new AtomicInteger();
        var semaphore = new Object();
        var fixture = stateFetcherFactory.createStateFetcher(() -> {
            synchronized (semaphore) {
                return value.incrementAndGet();
            }
        });

        // WHEN
        boolean hasDefinedState0 = fixture.hasDefinedState();
        var state1 = fixture.resolveState();
        boolean hasDefinedState1 = fixture.hasDefinedState();
        var state2 = fixture.resolveState();
        int state3;
        synchronized (semaphore) {
            fixture.refreshStateAsync(false);
            state3 = fixture.resolveState();
        }

        // THEN
        assertThat(hasDefinedState0).isFalse();
        assertThat(hasDefinedState1).isTrue();
        assertThat(state1).isEqualTo(1);
        assertThat(state2).isEqualTo(1);
        assertThat(state2).isSameAs(state1);
        assertThat(state3).isEqualTo(1);
    }

    @Test
    void testRefreshStateAsyncResetNow() {
        // GIVEN
        var value = new AtomicInteger();
        var semaphore = new Object();
        var fixture = stateFetcherFactory.createStateFetcher(() -> {
            synchronized (semaphore) {
                return value.incrementAndGet();
            }
        });

        // WHEN
        boolean hasDefinedState0 = fixture.hasDefinedState();
        var state1 = fixture.resolveState();
        boolean hasDefinedState1 = fixture.hasDefinedState();
        var state2 = fixture.resolveState();
        synchronized (semaphore) {
            fixture.refreshStateAsync(true);
        }
        int state3 = fixture.resolveState();

        // THEN
        assertThat(hasDefinedState0).isFalse();
        assertThat(hasDefinedState1).isTrue();
        assertThat(state1).isEqualTo(1);
        assertThat(state2).isEqualTo(1);
        assertThat(state2).isSameAs(state1);
        assertThat(state3).isEqualTo(2);
    }

    @Disabled
    @Test
    void testRefreshStateAsyncQueued() {
        forkJoinPoolGuard.reentrantInvokeOnForkJoinPool(() -> {
            // GIVEN
            var value = new AtomicInteger();
            var latchIndex = new AtomicInteger();
            int queueCount = 5;

            var fetchWaitLatches = new CountDownLatch[queueCount];
            for (int a = fetchWaitLatches.length; a-- > 0;) {
                fetchWaitLatches[a] = new CountDownLatch(1);
            }
            var fetchNotifyLatches = new CountDownLatch[fetchWaitLatches.length];

            var fixture = stateFetcherFactory.createStateFetcher(() -> {
                int currLatchIndex = latchIndex.getAndIncrement();
                try {
                    fetchNotifyLatches[currLatchIndex] = new CountDownLatch(1);
                    fetchWaitLatches[currLatchIndex].await();
                    return value.incrementAndGet();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } finally {
                    fetchNotifyLatches[currLatchIndex].countDown();
                }
            });

            // WHEN
            for (int a = queueCount; a-- > 0;) {
                fixture.ensureStateAsync();
            }

            for (int a = queueCount; a-- > 0;) {
                fetchWaitLatches[a].countDown();
            }
            for (int a = queueCount; a-- > 0;) {
                awaitIfValid(fetchNotifyLatches[a]);
            }

            Integer state0 = fixture.resolveState();

            // THEN
            assertThat(state0).isEqualTo(1);
        });
    }

    @SneakyThrows
    protected void awaitIfValid(CountDownLatch latch) {
        if (latch != null) {
            latch.await();
        }
    }
}
