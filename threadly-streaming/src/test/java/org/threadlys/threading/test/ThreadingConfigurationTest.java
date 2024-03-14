package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.threadlys.configuration.CommonsThreadingAutoConfiguration;
import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.threading.impl.ThreadingConfigurationValues;
import org.threadlys.threading.impl.ThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.TransferrableThreadScope;
import org.threadlys.threading.test.context.BeanWithAsync;
import org.threadlys.threading.test.context.BeanWithThreadLocalField;
import org.threadlys.threading.test.context.BeanWithThreadLocalProvider;
import org.threadlys.threading.test.context.BeanWithThreadLocalScope;
import org.threadlys.threading.test.context.TestService;
import org.threadlys.threading.test.context.TestServiceImpl;
import org.threadlys.threading.test.context.ThreadingBeanConfiguration;
import org.threadlys.utils.FutureUtil;
import org.threadlys.utils.ReflectUtil;
import lombok.SneakyThrows;

@Component
@ExtendWith(SpringExtension.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = { "threadlys.threading.filter-active=true", "threadlys.threading.task-executor-active=true", "threadlys.threading.timeout=#{T(java.time.Duration).parse(\"PT10M\")}",
        "threadlys.threading.gracePeriod=#{T(java.time.Duration).parse(\"PT0.5S\")}", "threadlys.threading.header-permitted=true", "threadlys.threading.transferrable-scope-active=true",
        "threadlys.threading.worker-timeout=#{T(java.time.Duration).parse(\"PT1M\")}", "threadlys.threading.worker-timeout-check-interval=10000" })
public class ThreadingConfigurationTest {
    @Configuration
    @Import({ BeanWithAsync.class, BeanWithThreadLocalField.class, BeanWithThreadLocalProvider.class, BeanWithThreadLocalScope.class, ThreadingBeanConfiguration.class,
            CommonsThreadingAutoConfiguration.class })
    @EnableAsync
    static class ContextConfiguration {
    }

    private static AtomicInteger threadPoolSequence = new AtomicInteger();

    @Autowired
    ForkJoinPoolGuard fjpGuard;

    @Autowired
    FutureUtil futureUtil;

    @Autowired
    TransferrableThreadScope scope;

    @Autowired
    ReflectUtil reflectUtil;

    @Autowired
    BeanWithThreadLocalField beanWithThreadLocalField;

    @Autowired
    BeanWithThreadLocalProvider beanWithThreadLocalProvider;

    @Autowired
    BeanWithThreadLocalScope beanWithThreadLocalScope;

    @Autowired
    BeanWithAsync beanWithAsync;

    @Autowired
    ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    TestServiceImpl impl;

    int workerCount;

    ForkJoinPool fjp;

    TestInfo testInfo;

    @BeforeEach
    public void beforeEach( TestInfo testInfo) {
        this.testInfo = testInfo;
        workerCount = 2;
        threadlyStreamingConfiguration.setPoolSize(workerCount);
        impl = new TestServiceImpl();
        BeanWithThreadLocalScope.destroyCount.set(0);
        BeanWithThreadLocalScope.customTransferCount.set(0);
        beanWithAsync.getInvocationCount()
                .clear();
    }

    @AfterEach
    public void afterEach() {
        if (fjp != null) {
            fjp.shutdownNow();
            fjp = null;
        }
        impl.invocationCount.clear();
        impl.lastValueTL.remove();
        impl = null;
        beanWithThreadLocalField.lastValueTL.remove();
        beanWithThreadLocalProvider.lastValueTL.remove();
        beanWithThreadLocalScope.setValue(null);
        beanWithThreadLocalScope.setCustomValue(null);
        beanWithAsync.getInvocationCount()
                .clear();
        scope.getThreadScopeMap()
                .clear();
        threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(null);
    }

    @Test
    public void callSetters() throws Exception {
        ThreadingConfigurationValues values = new ThreadingConfigurationValues().setFilterActive(false)
                .setTaskExecutorActive(false)
                .setMaximumPoolSize(0x3FFF)
                .setTimeout(Duration.ofMinutes(5))
                .setGracePeriod(Duration.ofMillis(400))
                .setWorkerTimeout(Duration.ofSeconds(45))
                .setThreadlyStreamingHeaderPermitted(false);

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(Duration.ofMinutes(1));

        threadlyStreamingConfiguration.setFilterActive(values.getFilterActive());
        threadlyStreamingConfiguration.setTaskExecutorActive(values.getTaskExecutorActive());
        threadlyStreamingConfiguration.setMaximumPoolSize(values.getMaximumPoolSize());
        threadlyStreamingConfiguration.setTimeout(values.getTimeout());
        threadlyStreamingConfiguration.setGracePeriod(values.getGracePeriod());
        threadlyStreamingConfiguration.setWorkerTimeout(values.getWorkerTimeout());

        // check modified state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isEqualTo(values.getFilterActive());
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isEqualTo(values.getTaskExecutorActive());
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(values.getMaximumPoolSize());
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(values.getTimeout());
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(values.getGracePeriod());
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(values.getWorkerTimeout());

        // reset overridden values
        threadlyStreamingConfiguration.setFilterActive(null);
        threadlyStreamingConfiguration.setTaskExecutorActive(null);
        threadlyStreamingConfiguration.setMaximumPoolSize(null);
        threadlyStreamingConfiguration.setTimeout(null);
        threadlyStreamingConfiguration.setGracePeriod(null);
        threadlyStreamingConfiguration.setWorkerTimeout(null);

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    public void callSetterComposite() throws Exception {
        ThreadingConfigurationValues values = new ThreadingConfigurationValues().setFilterActive(false)
                .setTaskExecutorActive(false)
                .setMaximumPoolSize(0x3FFF)
                .setTimeout(Duration.ofMinutes(5))
                .setGracePeriod(Duration.ofMillis(400))
                .setThreadlyStreamingHeaderPermitted(false);

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();

        threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(values);

        // check modified state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isEqualTo(values.getFilterActive());
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isEqualTo(values.getTaskExecutorActive());
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(values.getMaximumPoolSize());
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(values.getTimeout());
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(values.getGracePeriod());
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isEqualTo(values.getThreadlyStreamingHeaderPermitted());

        threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(null);

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();
    }

    @Test
    public void callSetterDistinctThreads() throws Exception {
        ThreadingConfigurationValues values = new ThreadingConfigurationValues().setFilterActive(false)
                .setTaskExecutorActive(false)
                .setMaximumPoolSize(0x3FFF)
                .setTimeout(Duration.ofMinutes(5))
                .setGracePeriod(Duration.ofMillis(400))
                .setWorkerTimeout(Duration.ofSeconds(45))
                .setThreadlyStreamingHeaderPermitted(false);

        fjp = fjpGuard.createForkJoinPool();

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        ForkJoinTask<?> future2 = fjp.submit(() -> {
            try {
                threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(values);

                // check modified state
                assertThat(threadlyStreamingConfiguration.isFilterActive()).isEqualTo(values.getFilterActive());
                assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isEqualTo(values.getTaskExecutorActive());
                assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(values.getMaximumPoolSize());
                assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(values.getTimeout());
                assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(values.getGracePeriod());
                assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(values.getWorkerTimeout());
                assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isEqualTo(values.getThreadlyStreamingHeaderPermitted());
                latch1.countDown();
            } finally {
                try {
                    latch2.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        if (!latch1.await(60, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();

        latch2.countDown();

        futureUtil.wait(future2);
    }

    @Test
    public void callSetterInherited() throws Exception {
        ThreadingConfigurationValues values = new ThreadingConfigurationValues().setFilterActive(false)
                .setTaskExecutorActive(false)
                .setMaximumPoolSize(0x3FFF)
                .setTimeout(Duration.ofMinutes(5))
                .setGracePeriod(Duration.ofMillis(400))
                .setWorkerTimeout(Duration.ofSeconds(45))
                .setThreadlyStreamingHeaderPermitted(false);

        fjp = fjpGuard.createForkJoinPool();

        // check original state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isTrue();
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(32767);
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(Duration.ofMillis(500));
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isTrue();

        threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(values);

        // check modified state
        assertThat(threadlyStreamingConfiguration.isFilterActive()).isEqualTo(values.getFilterActive());
        assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isEqualTo(values.getTaskExecutorActive());
        assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(values.getMaximumPoolSize());
        assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(values.getTimeout());
        assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(values.getGracePeriod());
        assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(values.getWorkerTimeout());
        assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isEqualTo(values.getThreadlyStreamingHeaderPermitted());

        fjpGuard.reentrantInvokeOnForkJoinPoolWithResult(() -> {
            // check modified state propagation in forked thread
            assertThat(threadlyStreamingConfiguration.isFilterActive()).isEqualTo(values.getFilterActive());
            assertThat(threadlyStreamingConfiguration.isTaskExecutorActive()).isEqualTo(values.getTaskExecutorActive());
            assertThat(threadlyStreamingConfiguration.getMaximumPoolSize()).isEqualTo(values.getMaximumPoolSize());
            assertThat(threadlyStreamingConfiguration.getTimeout()).isEqualTo(values.getTimeout());
            assertThat(threadlyStreamingConfiguration.getGracePeriod()).isEqualTo(values.getGracePeriod());
            assertThat(threadlyStreamingConfiguration.getWorkerTimeout()).isEqualTo(values.getWorkerTimeout());
            assertThat(threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()).isEqualTo(values.getThreadlyStreamingHeaderPermitted());
            return null;
        });
    }

    protected void nestedContextSnapshotIntern(String expectedValue1, String expectedValue2, ContextSnapshot cs1, ContextSnapshot cs2) {
        AtomicInteger phaseCounter = new AtomicInteger();
        cs1.scoped(() -> {
            assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isZero();

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

            phaseCounter.incrementAndGet();

            assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(1);
            assertThat(BeanWithThreadLocalScope.destroyCount.get()).isZero();

            cs2.scoped(() -> {
                assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(1);

                assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);
                phaseCounter.incrementAndGet();

                assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(2);

                return null;
            })
                    .get();

            assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(2);
            assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(1);

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

            assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(2);

            phaseCounter.incrementAndGet();
        })
                .run();

        assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(2);
        assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(2);

        assertThat(phaseCounter.get()).isEqualTo(3);
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

        phaseCounter.set(0);
        Arrays.asList(phaseCounter)
                .stream()
                .map(cs1.push())
                .map(item -> {
                    assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(2);

                    assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
                    assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
                    assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

                    assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(3);

                    phaseCounter.incrementAndGet();

                    assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(2);

                    Arrays.asList(item)
                            .stream()
                            .map(cs2.push())
                            .map(item2 -> {
                                assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(3);

                                assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
                                assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
                                assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

                                assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(4);

                                phaseCounter.incrementAndGet();
                                return null;
                            })
                            .map(cs2.pop())
                            .collect(Collectors.toList());

                    assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(4);
                    assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(3);

                    assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
                    assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
                    assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);
                    phaseCounter.incrementAndGet();

                    return item;
                })
                .map(cs1.pop())
                .collect(Collectors.toList());

        assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(4);
        assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(4);

        assertThat(phaseCounter.get()).isEqualTo(3);
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

        assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(4);
    }

    protected void assertThreadLocals(String message, String expectedValueForAll) {
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValueForAll).withFailMessage(() -> message);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValueForAll).withFailMessage(() -> message);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValueForAll).withFailMessage(() -> message);
    }

    protected void assertThreadLocals(String message, String expectedField, String expectedProvider, String expectedScope) {
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedField).withFailMessage(() -> message);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedProvider).withFailMessage(() -> message);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedScope).withFailMessage(() -> message);
    }

    protected void setThreadLocals(String value) {
        beanWithThreadLocalField.lastValueTL.set(value);
        beanWithThreadLocalProvider.lastValueTL.set(value);
        beanWithThreadLocalScope.setValue(value);
    }

    @SneakyThrows
    private void mockCompute() {
        Thread.sleep(10);
    }

    Map<String, Integer> buildTestData() {
        Map<String, Integer> invocations = new LinkedHashMap<>();
        for (int a = workerCount * 2; a-- > 0;) {
            invocations.put("hello" + a, impl.testOperation("hello" + a));
        }
        assertThat(impl.invocationCount.size()).isEqualTo(1);
        assertThat(impl.lastValueTL).isNotNull();

        impl.invocationCount.clear();
        impl.lastValueTL.remove();
        return invocations;
    }

    @SuppressWarnings("unchecked")
    private <T> T createAspectAwareProxy(Class<T> type, ForkJoinPoolGuard fjpGuard, Object target) {
        return (T) Proxy.newProxyInstance(Thread.currentThread()
                .getContextClassLoader(), new Class[] { type }, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return fjpGuard.reentrantInvokeOnForkJoinPoolWithResult(() -> reflectUtil.invokeMethod(method, target, args));
                    }
                });
    }

    List<?> withExecutor(int threadCount, Function<ExecutorService, Collection<Future<?>>> runnable) {
        String testName = testInfo.getTestMethod().get().getName();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            int threadPoolId = threadPoolSequence.incrementAndGet();

            AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName(testName + "-" + threadPoolId + "-" + seq.incrementAndGet());
                return thread;
            }
        });
        try {
            Collection<Future<?>> futures = runnable.apply(executorService);
            return futureUtil.allOf(futures);
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * build a proxy that ensures that the invoked method is always from a expected thread *
     *
     * @param fixture
     * @param entry
     * @param usedThreads
     * @return
     */
    protected int proxyAndInvokeFixture(TestService fixture, Entry<String, Integer> entry, Thread expectedThread, Map<Thread, Object> usedThreads) {
        TestService aspectProxy = createAspectAwareProxy(TestService.class, fjpGuard, fixture);
        return invokeFixture(aspectProxy, entry, usedThreads);
    }

    protected int invokeFixture(TestService fixture, Entry<String, Integer> entry, Map<Thread, Object> usedThreads) {
        // prove that the TL variable is in a determined state before execution
        if (usedThreads.containsKey(Thread.currentThread())) {
            assertThat(fixture.isClear()).isFalse();
        } else {
            assertThat(fixture.isClear()).isTrue();
        }
        usedThreads.put(Thread.currentThread(), Boolean.TRUE);

        int result = fixture.testOperation(entry.getKey());
        assertThat(result).isEqualTo(entry.getValue());

        // prove that the TL variable is in a determined state after execution
        if (fixture.isClear()) {
            assertThat(fixture.isClear()).isFalse();
        }
        return result;
    }
}
