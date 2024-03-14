package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.threadlys.configuration.AsyncMethodConfiguration;
import org.threadlys.configuration.CommonsThreadingAutoConfiguration;
import org.threadlys.configuration.CommonsThreadingSpringConfig;
import org.threadlys.streams.CheckedSupplier;
import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.threading.ContextSnapshotLifecycleListener;
import org.threadlys.threading.ContextSnapshotLifecycleListenerExtendable;
import org.threadlys.threading.TaskExecutorListener;
import org.threadlys.threading.TaskExecutorListenerExtendable;
import org.threadlys.threading.ThreadLocalTransferrerExtendable;
import org.threadlys.threading.impl.ConcurrentProcessingFilter;
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.threading.impl.ThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.TransferrableRequestContext;
import org.threadlys.threading.impl.TransferrableSecurityContext;
import org.threadlys.threading.impl.TransferrableThreadScope;
import org.threadlys.threading.test.context.BeanWithAsync;
import org.threadlys.threading.test.context.BeanWithThreadLocalField;
import org.threadlys.threading.test.context.BeanWithThreadLocalProvider;
import org.threadlys.threading.test.context.BeanWithThreadLocalScope;
import org.threadlys.threading.test.context.TestService;
import org.threadlys.threading.test.context.TestServiceImpl;
import org.threadlys.utils.FutureUtil;
import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.ReflectUtil;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.StateRollback;
import org.threadlys.utils.configuration.CommonsUtilsSpringConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@TestPropertySource(properties = { "threadlys.threading.filter-active=true", "threadlys.threading.task-executor-active=true",
                "threadlys.threading.timeout=#{T(java.time.Duration).parse(\"PT10M\")}", "threadlys.threading.gracePeriod=#{T(java.time.Duration).parse(\"PT0.5S\")}",
                "threadlys.threading.header-permitted=true", "threadlys.threading.transferrable-scope-active=true" })
class ConcurrentProcessingTest {
    @Component
    @Primary
    static class InstrumentedConcurrentProcessingFilter extends ConcurrentProcessingFilter {
        public static boolean invalidJsonInput;

        @Override
        protected void handleInvalidJsonInput(JsonProcessingException e) {
            // suppress error logging to not pollute the console
            invalidJsonInput = true;
        }
    }

    @Configuration
    @Import({ BeanWithAsync.class, BeanWithThreadLocalField.class, BeanWithThreadLocalProvider.class, BeanWithThreadLocalScope.class, TestServiceImpl.class, CommonsThreadingSpringConfig.class,
                    CommonsUtilsSpringConfig.class, CommonsThreadingAutoConfiguration.class, AsyncMethodConfiguration.class, InstrumentedConcurrentProcessingFilter.class })
    static class ContextConfiguration {
        @Bean
        ConcurrentProcessingFilter concurrentProcessingFilter() {
            return new ConcurrentProcessingFilter();
        }
    }

    private static AtomicInteger threadPoolSequence = new AtomicInteger();

    @Autowired
    TestService testService;

    @Autowired
    ForkJoinPoolGuard fjpGuard;

    @Autowired
    FutureUtil futureUtil;

    @Autowired
    TransferrableThreadScope scope;

    @Autowired
    ReflectUtil reflectUtil;

    @Autowired
    SneakyThrowUtil sneakyThrowUtil;

    @Autowired
    ContextSnapshotFactory contextSnapshotFactory;

    @Autowired
    ContextSnapshotLifecycleListenerExtendable csListenerRegistry;

    @Autowired
    TaskExecutorListenerExtendable teListenerRegistry;

    @Autowired
    BeanWithThreadLocalField beanWithThreadLocalField;

    @Autowired
    BeanWithThreadLocalProvider beanWithThreadLocalProvider;

    @Autowired
    BeanWithThreadLocalScope beanWithThreadLocalScope;

    @Autowired
    ThreadLocalTransferrerExtendable threadLocalTransferrerExtendable;

    @Autowired
    BeanWithAsync beanWithAsync;

    @Autowired
    ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    @Autowired
    ConcurrentProcessingFilter concurrentProcessingFilter;

    @Autowired
    TaskExecutor taskExecutor;

    TestServiceImpl impl;

    int workerCount;

    ForkJoinPool fjp;

    TestInfo testInfo;

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        this.testInfo = testInfo;

        workerCount = 2;
        threadlyStreamingConfiguration.setPoolSize(workerCount);
        impl = new TestServiceImpl();
        BeanWithThreadLocalScope.createCount.set(0);
        BeanWithThreadLocalScope.destroyCount.set(0);
        BeanWithThreadLocalScope.customTransferCount.set(0);
        beanWithAsync.getInvocationCount().clear();
        InstrumentedConcurrentProcessingFilter.invalidJsonInput = false;
    }

    @AfterEach
    void afterEach() {
        if (fjp != null) {
            fjp.shutdownNow();
            fjp = null;
        }
        impl.invocationCount.clear();
        impl.lastValueTL.remove();
        impl = null;
        beanWithThreadLocalField.lastValueTL.remove();
        beanWithThreadLocalProvider.lastValueTL.remove();
        beanWithAsync.getInvocationCount().clear();
        scope.getAndRemoveThreadScopeMap();
    }

    /**
     * ensures that the scoped thread-local bean is really target-proxied (means: resolved-on-invoke and not resolved-on-inject)
     */
    @Test
    void scopeActive() {
        assertThat(beanWithThreadLocalScope.getClass()).isNotSameAs(BeanWithThreadLocalScope.class);
        assertThat(beanWithThreadLocalScope).isNotSameAs(beanWithThreadLocalScope.getThis()[0]);
    }

    /**
     * two consecutive invocations of parallelStreams() use the same globally shared thread pool. a raw ThreadLocal variable is not cleaned between those fork occurrences a worker-state from forked
     * execution 1 leaks data into a worker-state from forked execution 2. in a web server scenario this can even expose sensitive user data also spring request scope doesnt fundamentally help here as
     * Spring is not managing thread-local variables of the common ForkJoinPool - only from Tomcat or Spring managed threads
     */
    @Test
    @Disabled("due to a race condition")
    void proofMemoryLeakOnCommons() {
        Map<String, Integer> invocations = buildTestData();

        Collection<Integer> resultItems = impl.testOperationBatch(invocations.keySet());
        assertThat(invocations).hasSize(resultItems.size());

        List<Thread> leakingThreads = invocations.keySet().parallelStream().map(item -> impl.lastValueTL.get() != null ? Thread.currentThread() : null).filter(thread -> thread != null).distinct()
                        .collect(Collectors.toList());

        assertThat(leakingThreads).isNotEmpty();
    }

    /**
     * same scenario as in {@link #proofMemoryLeakOnCommons()} but now each forked execution runs with a distinct local, non-shared ForkJoinPool. This makes the Java Streams API to not use the common
     * ForkJoinPool and therefore avoids leaking any worker state to unmanaged threads
     *
     * @throws Throwable
     */
    @Test
    void proofMemoryLeakOnCommonsFixable() throws Throwable {
        Map<String, Integer> invocations = buildTestData();

        ForkJoinPool fjp1 = fjpGuard.createForkJoinPool();
        try {
            Collection<Integer> resultItems = fjp1.submit(() -> impl.testOperationBatch(invocations.keySet())).get(30, TimeUnit.MINUTES);
            assertThat(invocations).hasSize(resultItems.size());
        } finally {
            fjp1.shutdownNow();
        }

        ForkJoinPool fjp2 = fjpGuard.createForkJoinPool();
        try {
            List<Thread> leakingThreads = fjp2.submit(
                            () -> invocations.keySet().parallelStream().map(item -> impl.lastValueTL.get() != null ? Thread.currentThread() : null).filter(thread -> thread != null).distinct()
                                            .collect(Collectors.toList())).get(30, TimeUnit.MINUTES);
            assertThat(leakingThreads).isEmpty();
        } finally {
            fjp2.shutdownNow();
        }
    }

    /**
     * Tests that two consecutive usages of distinct snapshots work properly and also rollback properly It also shows that a thread-local scoped Spring bean behaves differently than a raw thread-local
     * value here
     *
     * @throws Throwable
     */
    @Test
    void nestedContextSnapshot() throws Throwable {
        fjp = fjpGuard.createForkJoinPool();

        String expectedValue1 = "hello";
        String expectedValue2 = "hello2";
        beanWithThreadLocalField.lastValueTL.set(expectedValue1);
        beanWithThreadLocalProvider.lastValueTL.set(expectedValue1);
        beanWithThreadLocalScope.setValue(expectedValue1);

        ContextSnapshot cs1 = contextSnapshotFactory.createSnapshot();

        beanWithThreadLocalField.lastValueTL.set(expectedValue2);
        beanWithThreadLocalProvider.lastValueTL.set(expectedValue2);
        beanWithThreadLocalScope.setValue(expectedValue2);

        ContextSnapshot cs2 = contextSnapshotFactory.createSnapshot();

        // first run it for the current normal thread
        nestedContextSnapshotIntern(expectedValue1, expectedValue2, cs1, cs2);

        BeanWithThreadLocalScope.createCount.set(0);
        BeanWithThreadLocalScope.customTransferCount.set(0);
        BeanWithThreadLocalScope.destroyCount.set(0);

        // then proof that it works for an async thread as well
        futureUtil.wait(fjp.submit(() -> {
            beanWithThreadLocalField.lastValueTL.set(expectedValue2);
            beanWithThreadLocalProvider.lastValueTL.set(expectedValue2);
            beanWithThreadLocalScope.setValue(expectedValue2);

            nestedContextSnapshotIntern(expectedValue1, expectedValue2, cs1, cs2);
        }));
    }

    /**
     * Tests that a thread-local bean that is allocated in master, but never lazy-initialized in fork1 still gets transferred to fork2 of fork1
     */
    @Test
    void transitiveThreadLocalBean() {
        fjp = fjpGuard.createForkJoinPool();

        String expectedValue1 = "hello";
        beanWithThreadLocalScope.setValue(expectedValue1);

        ContextSnapshot cs1 = contextSnapshotFactory.createSnapshot();

        withExecutor(1, executor1 -> {
            futureUtil.wait(executor1.submit(cs1.scoped(() -> {
                withExecutor(1, executor2 -> {
                    ContextSnapshot cs2 = contextSnapshotFactory.createSnapshot();
                    futureUtil.wait(executor2.submit(cs2.scoped(() -> {
                        assertThat(expectedValue1).isEqualTo(beanWithThreadLocalScope.getValue());
                    })));
                    return null;
                });
            })));

            return null;
        });
    }

    protected void nestedContextSnapshotIntern(String expectedValue1, String expectedValue2, ContextSnapshot cs1, ContextSnapshot cs2) {
        assertBeanWithThreadLocalScopeCounter(1, 0, 0);

        AtomicInteger phaseCounter = new AtomicInteger();
        cs1.scoped(() -> {
            assertBeanWithThreadLocalScopeCounter(1, 0, 0);

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

            phaseCounter.incrementAndGet();

            assertBeanWithThreadLocalScopeCounter(2, 1, 0);

            cs2.scoped(() -> {
                assertBeanWithThreadLocalScopeCounter(2, 1, 0);

                assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);
                phaseCounter.incrementAndGet();

                assertBeanWithThreadLocalScopeCounter(3, 2, 0);

                return null;
            }).get();

            assertBeanWithThreadLocalScopeCounter(3, 2, 1);

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

            assertBeanWithThreadLocalScopeCounter(3, 2, 1);

            phaseCounter.incrementAndGet();
        }).run();

        assertBeanWithThreadLocalScopeCounter(3, 2, 2);

        assertThat(phaseCounter.get()).isEqualTo(3);
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

        phaseCounter.set(0);
        Arrays.asList(phaseCounter).stream().map(cs1.push()).map(item -> {
            assertBeanWithThreadLocalScopeCounter(3, 2, 2);

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);

            assertBeanWithThreadLocalScopeCounter(4, 3, 2);

            phaseCounter.incrementAndGet();

            assertBeanWithThreadLocalScopeCounter(4, 3, 2);

            Arrays.asList(item).stream().map(cs2.push()).map(item2 -> {
                assertBeanWithThreadLocalScopeCounter(4, 3, 2);

                assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
                assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

                assertBeanWithThreadLocalScopeCounter(5, 4, 2);

                phaseCounter.incrementAndGet();
                return null;
            }).map(cs2.pop()).collect(Collectors.toList());

            assertBeanWithThreadLocalScopeCounter(5, 4, 3);

            assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue1);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue1);
            phaseCounter.incrementAndGet();

            return item;
        }).map(cs1.pop()).collect(Collectors.toList());

        assertBeanWithThreadLocalScopeCounter(5, 4, 4);

        assertThat(phaseCounter.get()).isEqualTo(3);
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue2);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValue2);

        assertBeanWithThreadLocalScopeCounter(5, 4, 4);
    }

    @Test
    void transferrableAnnotationEffective() throws Throwable {
        fjp = fjpGuard.createForkJoinPool();

        String expectedValue = "hello";
        beanWithThreadLocalField.lastValueTL.set(expectedValue);
        beanWithThreadLocalProvider.lastValueTL.set(expectedValue);
        beanWithThreadLocalScope.setValue(expectedValue);
        Map<String, Integer> invocations = buildTestData();

        ContextSnapshot cs = contextSnapshotFactory.createSnapshot();

        fjp.submit(() -> {
            long count = invocations.entrySet().stream().parallel().map(entry -> {
                assertThreadLocals("threadLocal not in a clean state", null, null, "default-initializer");
                return entry;
            }).map(cs.push()).map(entry -> {
                assertThreadLocals("threadLocal has not the transferred value", expectedValue);
                return entry;
            }).map(cs.pop()).map(eitherEntry -> {
                assertThreadLocals("threadLocal not in a clean state", null, null, "default-initializer");
                return eitherEntry;
            }).distinct().count();
            assertThat(invocations).hasSize((int) count);
        }).get(30, TimeUnit.MINUTES);

        fjp.submit(() -> {
            long count = invocations.entrySet().stream().parallel().map(entry -> {
                assertThreadLocals("threadLocal not in a clean state", null, null, "default-initializer");
                return entry;
            }).map(cs.scoped(entry -> {
                assertThreadLocals("threadLocal has not the transferred value", expectedValue);
                return entry;
            })).map(eitherEntry -> {
                assertThreadLocals("threadLocal not in a clean state", null, null, "default-initializer");
                return eitherEntry;
            }).distinct().count();
            assertThat(invocations).hasSize((int) count);
        }).get(30, TimeUnit.MINUTES);
        assertThreadLocals("threadLocal has not the original value", expectedValue);
    }

    protected void assertThreadLocals(String message, String expectedValueForAll) {
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValueForAll);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValueForAll);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedValueForAll);
    }

    protected void assertThreadLocals(String message, String expectedField, String expectedProvider, String expectedScope) {
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedField);
        assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedProvider);
        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(expectedScope);
    }

    protected void setThreadLocals(String value) {
        beanWithThreadLocalField.lastValueTL.set(value);
        beanWithThreadLocalProvider.lastValueTL.set(value);
        beanWithThreadLocalScope.setValue(value);
    }

    @Test
    void transferrableAnnotationEffectiveButMemoryLeak() throws Throwable {
        fjp = fjpGuard.createForkJoinPool();

        String expectedValue = "hello";
        beanWithThreadLocalField.lastValueTL.set(expectedValue);
        beanWithThreadLocalProvider.lastValueTL.set(expectedValue);
        Map<String, Integer> invocations = buildTestData();

        ContextSnapshot cs = contextSnapshotFactory.createSnapshot();
        fjp.submit(() -> {
            AtomicInteger assertionErrors = new AtomicInteger();
            AtomicInteger providerAssertionErrors = new AtomicInteger();
            long count = invocations.entrySet().stream().parallel().map(entry -> {
                if (beanWithThreadLocalField.lastValueTL.get() != null) {
                    assertionErrors.incrementAndGet();
                    beanWithThreadLocalField.lastValueTL.remove();
                }
                if (beanWithThreadLocalProvider.lastValueTL.get() != null) {
                    providerAssertionErrors.incrementAndGet();
                    beanWithThreadLocalProvider.lastValueTL.remove();
                }
                return entry;
            }).map(cs.push()).map(entry -> {
                assertThat(beanWithThreadLocalField.lastValueTL.get()).isEqualTo(expectedValue);
                assertThat(beanWithThreadLocalProvider.lastValueTL.get()).isEqualTo(expectedValue);
                mockCompute();
                return entry;
            }).distinct().count();
            assertThat(invocations).hasSize((int) count);
            assertThat(assertionErrors.get()).isNotZero();
            assertThat(providerAssertionErrors.get()).isNotZero();
        }).get(30, TimeUnit.MINUTES);
        assertThat(beanWithThreadLocalField.lastValueTL.get()).isSameAs(expectedValue);
    }

    @Test
    void rawAspectLogicSimple() {
        Map<String, Integer> invocations = buildTestData();

        TestService proxy = createAspectAwareProxy(TestService.class, fjpGuard, impl);
        assertThat(proxy.isClear()).isTrue();

        Map<Thread, Object> usedThreads = doJoinPointInvocation(impl, workerCount, invocations, null, false);
        assertThat(usedThreads).isNotEmpty();
    }

    /**
     * Proof that Spring {@link Async} works properly
     *
     * @throws Exception
     */
    @Test
    void asyncActive() throws Exception {
        String expectedTlValue = "master";
        String expectedValue = "hello";
        asyncActive(expectedTlValue, expectedValue, () -> beanWithAsync.getSomethingAsync(expectedValue));
    }

    /**
     * Proof that Spring {@link Async} works properly with CompletableFuture specifically as well
     *
     * @throws Exception
     */
    @Test
    void asyncActiveCompletable() throws Exception {
        String expectedTlValue = "master";
        String expectedValue = "hello";
        asyncActive(expectedTlValue, expectedValue, () -> beanWithAsync.getSomethingAsyncCompletable(expectedValue));
    }

    /**
     * Proof that the Spring SecurityContext is properly transferred
     *
     * @throws Exception
     */
    @Test
    void securityContext() throws Exception {
        Authentication myAuth = Mockito.mock(Authentication.class);
        IStateRollback rollback = TransferrableSecurityContext.pushAuthentication(myAuth);
        try {
            // 1) makes sure that all calls to fjp.currentForkJoinPool() get a valid
            // instance
            // 2) it implicitly does a context snaphot after we changed the thread-local
            // variable in the pushAuthentication() before
            fjpGuard.reentrantInvokeOnForkJoinPool(() -> {
                Thread currentThread = Thread.currentThread();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(myAuth);
                futureUtil.wait(beanWithAsync.invokeSupplierSpringAsync(() -> {
                    assertThat(Thread.currentThread()).isNotEqualTo(currentThread);
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(myAuth);
                    return null;
                }));
            });
        } finally {
            rollback.rollback();
        }
    }

    /**
     * Proof that the Spring RequestContext is properly transferred
     *
     * @throws Exception
     */
    @Test
    void requestScopedBean() throws Exception {
        RequestAttributes rq = Mockito.mock(RequestAttributes.class);
        IStateRollback rollback = TransferrableRequestContext.pushRequestAttributes(rq);
        try {
            // 1) makes sure that all calls to fjp.currentForkJoinPool() get a valid
            // instance
            // 2) it implicitly does a context snaphot after we changed the thread-local
            // variable in the pushRequestAttributes() before
            fjpGuard.reentrantInvokeOnForkJoinPool(() -> {
                Thread currentThread = Thread.currentThread();
                assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(rq);
                futureUtil.wait(beanWithAsync.invokeSupplierSpringAsync(() -> {
                    assertThat(Thread.currentThread()).isNotEqualTo(currentThread);
                    assertThat(RequestContextHolder.getRequestAttributes()).isSameAs(rq);
                    return null;
                }));
            });
        } finally {
            rollback.rollback();
        }
    }

    @Test
    void threadLocalTransferrer() {
        var value = "helloCustom";
        beanWithThreadLocalScope.setCustomValue(value);
        IStateRollback rollback = threadLocalTransferrerExtendable.registerThreadLocalTransferrer((masterBean, forkedBean) -> {
            forkedBean.setCustomValue(masterBean.getCustomValue());
        }, BeanWithThreadLocalScope.class);
        try {
            // 1) makes sure that all calls to fjp.currentForkJoinPool() get a valid
            // instance
            // 2) it implicitly does a context snaphot after we changed the thread-local
            // variable in the setCustomValue() before as well as
            // pushThreadLocalTransferrer()
            fjpGuard.reentrantInvokeOnForkJoinPool(() -> {
                Thread currentThread = Thread.currentThread();
                assertThat(beanWithThreadLocalScope.getCustomValue()).isSameAs(value);
                futureUtil.wait(beanWithAsync.invokeSupplierSpringAsync(() -> {
                    assertThat(Thread.currentThread()).isNotEqualTo(currentThread);
                    assertThat(beanWithThreadLocalScope.getCustomValue()).isSameAs(value);
                    return null;
                }));
            });
        } finally {
            rollback.rollback();
        }
    }

    @Test
    void threadLocalSpringBeanRestored() throws Exception {
        var value = "helloCustom";

        beanWithThreadLocalScope.setCustomValue(null);
        beanWithThreadLocalScope.setValue(null);

        assertThat(beanWithThreadLocalScope.getValue()).isNull();
        assertThat(beanWithThreadLocalScope.getCustomValue()).isNull();

        var cs = contextSnapshotFactory.createSnapshot();
        cs.scoped(() -> {
            beanWithThreadLocalScope.setCustomValue(value);
            beanWithThreadLocalScope.setValue(value);

            assertThat(beanWithThreadLocalScope.getValue()).isNotNull();
            assertThat(beanWithThreadLocalScope.getCustomValue()).isNotNull();
        });

        assertThat(beanWithThreadLocalScope.getValue()).isNull();
        assertThat(beanWithThreadLocalScope.getCustomValue()).isNull();

        assertThat(fjpGuard.currentForkJoinPool()).isNull();
        var fjp = fjpGuard.getDefaultForkJoinPool();
        assertThat(fjpGuard.currentForkJoinPool()).isNull();
        var rollback = fjpGuard.pushForkJoinPool(fjp);
        try {
            assertThat(fjpGuard.currentForkJoinPool()).isSameAs(fjp);

            CyclicBarrier barrier = new CyclicBarrier(fjp.getParallelism() + 1);
            AtomicReference<Exception> ex = new AtomicReference<>();

            for (int a = fjp.getParallelism(); a-- > 0; ) {
                taskExecutor.execute(() -> {
                    try {
                        beanWithThreadLocalScope.setCustomValue(value);
                        beanWithThreadLocalScope.setValue(value);

                        barrier.await(60, TimeUnit.SECONDS);

                        assertThat(beanWithThreadLocalScope.getValue()).isNotNull();
                        assertThat(beanWithThreadLocalScope.getCustomValue()).isNotNull();
                    } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                        ex.set(e);
                    }
                });
            }
            barrier.await(60, TimeUnit.SECONDS);

            if (ex.get() != null) {
                throw ex.get();
            }

            assertThat(beanWithThreadLocalScope.getValue()).isNull();
            assertThat(beanWithThreadLocalScope.getCustomValue()).isNull();

            // now ensure that none of the prior TL spring beans leak into the new "pool lifecycle"

            CyclicBarrier barrier2 = new CyclicBarrier(fjp.getParallelism() + 1);

            for (int a = fjp.getParallelism(); a-- > 0; ) {
                taskExecutor.execute(() -> {
                    try {
                        assertThat(beanWithThreadLocalScope.getValue()).isNull();
                        assertThat(beanWithThreadLocalScope.getCustomValue()).isNull();

                        barrier2.await(60, TimeUnit.SECONDS);

                    } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                        ex.set(e);
                    }
                });
            }
            barrier2.await(60, TimeUnit.SECONDS);

            if (ex.get() != null) {
                throw ex.get();
            }
        } finally {
            rollback.rollback();
        }
    }

    @Test
    void concurrentProcessingFilterActive() throws Exception {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        concurrentProcessingFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        assertThat(httpServletResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentProcessingFilterNotActive() throws Exception {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        httpServletRequest.addHeader(ConcurrentProcessingFilter.THREADLY_HEADER_KEY, "{\"filterActive\": false}");

        concurrentProcessingFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        assertThat(httpServletResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void concurrentProcessingFilterInvalidJson() throws Exception {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpServletResponse = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        httpServletRequest.addHeader(ConcurrentProcessingFilter.THREADLY_HEADER_KEY, "{\"filterActive: false}");

        concurrentProcessingFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        assertThat(InstrumentedConcurrentProcessingFilter.invalidJsonInput).isTrue();

        assertThat(httpServletResponse.getStatus()).isEqualTo(200);
    }

    /**
     * Proof that a worker from the ForkJoinPool doesnt get in a dirty interrupt state if an interrupt exception was thrown and not properly handled
     */
    @Test
    void clearedInterruptOnWorkers() throws Exception {
        fjp = fjpGuard.createForkJoinPool();

        // Object latch11 = new Object();
        // Object latch12 = new Object();
        Object latch2 = new Object();
        Object latch3 = new Object();

        CyclicBarrier latch1 = new CyclicBarrier(2);
        // CyclicBarrier latch2 = new CyclicBarrier(2);
        // CountDownLatch latch3 = new CountDownLatch(1);

        Thread currentThread = Thread.currentThread();
        Map<Thread, AtomicInteger> interruptCounterMap = new ConcurrentHashMap<>();

        fjp.execute(() -> {
            AtomicInteger interruptCounter = interruptCounterMap.computeIfAbsent(Thread.currentThread(), key -> new AtomicInteger());
            try {
                try {
                    assertThat(Thread.currentThread()).isNotEqualTo(currentThread);
                    latch1.await();
                    synchronized (latch2) {
                        latch2.wait();
                    }
                    // latch1.await();
                    // latch2.await();
                } catch (InterruptedException e) {
                    // latch3.countDown();
                    interruptCounter.incrementAndGet();
                    synchronized (latch3) {
                        latch3.notify();
                    }
                }
            } catch (Throwable e) {
                throw sneakyThrowUtil.sneakyThrow(e);
            }
        });
        latch1.await();

        // at this point we know that the worker thread is waiting on latch2, so we
        // interrupt him
        Entry<Thread, AtomicInteger> entry = interruptCounterMap.entrySet().iterator().next();
        entry.getKey().interrupt();

        // now we wait for the interupt signal to be propagated
        synchronized (latch3) {
            latch3.wait();
        }
        // at this point we know that the worker thread has terminated but has a
        // cleared interrupt signal
        assertThat(entry.getValue().get()).isEqualTo(1);
        assertThat(entry.getKey().isInterrupted()).isFalse();
    }

    private void asyncActive(String expectedTlValue, String expectedValue, CheckedSupplier<Future<String>> supplier) throws Exception {
        beanWithThreadLocalScope.setValue(expectedTlValue);

        Map<Thread, Integer> csInvocations = new ConcurrentHashMap<>();
        Map<Thread, Integer> teInvocations = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        var rollback = StateRollback.chain(chain -> {
            chain.append(csListenerRegistry.registerContextSnapshotLifecycleListener(new ContextSnapshotLifecycleListener() {
                @Override
                public void contextSnapshotApplied(ContextSnapshot contextSnapshot) {
                    csInvocations.compute(Thread.currentThread(), (thread, existingValue) -> existingValue == null ? 1 : existingValue + 1);
                }
            }));
            chain.append(teListenerRegistry.registerTaskExecutorListener(new TaskExecutorListener() {
                @Override
                public void taskExecuted(Runnable command) {
                    teInvocations.compute(Thread.currentThread(), (thread, existingValue) -> existingValue == null ? 1 : existingValue + 1);
                    latch.countDown();
                }

                @SneakyThrows
                @Override
                public void taskQueued(Runnable command) {
                    latch.await();
                }
            }));
        });
        try {
            Future<String> valueF = fjpGuard.reentrantInvokeOnForkJoinPoolWithResult(supplier);

            String value = valueF.get();

            Map<Thread, Integer> invocationCount = beanWithAsync.getInvocationCount();

            assertThat(csInvocations.values().stream().reduce(0, Integer::sum)).isEqualTo(2);
            assertThat(invocationCount.values().stream().reduce(0, Integer::sum)).isEqualTo(1);

            assertThat(value).isEqualTo(expectedTlValue + "#" + expectedValue);
            assertThat(beanWithAsync.getInvocationCount()).hasSize(1);
            assertThat(beanWithAsync.getInvocationCount().keySet().iterator().next()).isNotEqualTo(Thread.currentThread());
        } finally {
            rollback.rollback();
        }
    }

    @SneakyThrows
    private void mockCompute() {
        Thread.sleep(10);
    }

    private Map<Thread, Object> doJoinPointInvocation(TestService target, int workerCount, Map<String, Integer> invocations, AtomicInteger assertionErrorRaised, boolean doProxy) {
        Map<Thread, Object> usedThreads = new ConcurrentHashMap<>();
        withExecutor(workerCount, executor -> {
            List<Future<?>> futures2 = new ArrayList<>();
            invocations.entrySet().forEach(entry -> {
                Future<?> future = executor.submit(() -> {
                    try {
                        if (doProxy) {
                            return proxyAndInvokeFixture(target, entry, Thread.currentThread(), usedThreads);
                        } else {
                            return invokeFixture(target, entry, usedThreads);
                        }
                    } catch (AssertionError e) {
                        if (assertionErrorRaised == null) {
                            throw e;
                        }
                        assertionErrorRaised.incrementAndGet();
                        // intentionally suppress our expected exception
                        return 0;
                    }
                });
                futures2.add(future);
            });
            return futures2;
        });
        return usedThreads;
    }

    private Map<String, Integer> buildTestData() {
        Map<String, Integer> invocations = new LinkedHashMap<>();
        for (int a = workerCount * 2; a-- > 0; ) {
            invocations.put("hello" + a, impl.testOperation("hello" + a));
        }
        assertThat(impl.invocationCount).hasSize(1);
        assertThat(impl.lastValueTL).isNotNull();

        impl.invocationCount.clear();
        impl.lastValueTL.remove();
        return invocations;
    }

    @SuppressWarnings("unchecked")
    private <T> T createAspectAwareProxy(Class<T> type, ForkJoinPoolGuard fjpGuard, Object target) {
        return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { type }, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return fjpGuard.reentrantInvokeOnForkJoinPoolWithResult(() -> reflectUtil.invokeMethod(method, target, args));
            }
        });
    }

    private List<?> withExecutor(int threadCount, Function<ExecutorService, Collection<Future<?>>> runnable) {
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

    @Disabled
    @Test
    void beanProxied() {
        assertThat(testService).isNotExactlyInstanceOf(TestServiceImpl.class);
    }

    @Test
    void beanInvoked() {
        int result = testService.testOperation("hello");
        assertThat(result).isEqualTo(5);
    }

    @Test
    void parallelStreamsSuppressed() {
        var data = buildTestData().keySet();
        var cs = contextSnapshotFactory.createSnapshot();

        assertThat(data.stream().isParallel()).isFalse();
        assertThat(cs.parallel(data.stream()).isParallel()).isTrue();
        assertThat(cs.parallel(data.stream().parallel()).isParallel()).isTrue();
        assertThat(cs.parallel(data.parallelStream()).isParallel()).isTrue();

        threadlyStreamingConfiguration.setParallelActive(false);
        try {
            assertThat(data.stream().isParallel()).isFalse();
            assertThat(cs.parallel(data.stream()).isParallel()).isFalse();
            assertThat(cs.parallel(data.stream().parallel()).isParallel()).isFalse();
            assertThat(cs.parallel(data.parallelStream()).isParallel()).isFalse();
        } finally {
            threadlyStreamingConfiguration.setParallelActive(true);
        }
    }

    /**
     * Tests that a thread-local bean that is allocated in master, but never lazy-initialized in a nested scope (of the same master thread) gets the initial state represented from before the scope
     * entering properly restored
     */
    @Test
    void singleThreadUninitializedSnapshotRestored() {
        var data = buildTestData().keySet();
        var value = "value11";

        // no TL scoped bean at the very beginning
        assertBeanWithThreadLocalScopeCounter(0, 0, 0);

        beanWithThreadLocalScope.setValue(value);
        beanWithThreadLocalScope.setCustomValue(value);

        // TL scoped bean was lazily created on first access by current thread
        assertBeanWithThreadLocalScopeCounter(1, 0, 0);

        var cs = contextSnapshotFactory.createSnapshot();

        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(value);
        assertThat(beanWithThreadLocalScope.getCustomValue()).isEqualTo(value);

        // the snapshot itself did not change anything on or with the current TL scoped bean
        assertBeanWithThreadLocalScopeCounter(1, 0, 0);

        data.stream().forEach(item -> {
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(value);
            assertThat(beanWithThreadLocalScope.getCustomValue()).isEqualTo(value);
            // note that with entering the scope the current TL scoped bean get saved in the background but purged from the current thread
            // this means that the next access to it within the scope - which we won't do in this test scenario - would create a fresh instance
            // for the scope.
            cs.scopedConsumer(currItem -> {
                // important to NOT set or get any value from the TL scoped bean here
                // we really want to test what happens with a TL bean snapshot restore that was never initialized in the scope
                assertBeanWithThreadLocalScopeCounter(1, 0, 0);
            }).accept(item);
            assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(value);
            assertThat(beanWithThreadLocalScope.getCustomValue()).isEqualTo(value);

            // because we never initialized the TL scoped bean in the scope there was also nothing to destroy at the end of the scope
            assertBeanWithThreadLocalScopeCounter(1, 0, 0);
        });

        assertBeanWithThreadLocalScopeCounter(1, 0, 0);

        assertThat(beanWithThreadLocalScope.getValue()).isEqualTo(value);
        assertThat(beanWithThreadLocalScope.getCustomValue()).isEqualTo(value);
    }

    protected void assertBeanWithThreadLocalScopeCounter(int createCount, int customTransferCount, int destroyCount) {
        assertThat(BeanWithThreadLocalScope.createCount.get()).isEqualTo(createCount);
        assertThat(BeanWithThreadLocalScope.customTransferCount.get()).isEqualTo(customTransferCount);
        assertThat(BeanWithThreadLocalScope.destroyCount.get()).isEqualTo(destroyCount);
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
        assertThat(result).isEqualTo((int) entry.getValue());

        // prove that the TL variable is in a determined state after execution
        if (fixture.isClear()) {
            assertThat(fixture.isClear()).isFalse();
        }
        return result;
    }
}
