package org.threadlys.configuration;

import org.threadlys.digest.impl.ThreadLocalMessageDigestImpl;
import org.threadlys.state.StateFetcherFactoryImpl;
import org.threadlys.state.StateFetcherFactoryInternal;
import org.threadlys.threading.impl.TransferrableApplicationContext;
import org.threadlys.threading.impl.TransferrableThreadLocalsImpl;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import org.threadlys.streams.impl.AsyncDataProcessorImpl;
import org.threadlys.threading.impl.ConcurrentProcessingFilter;
import org.threadlys.threading.impl.ContextSnapshotControllerImpl;
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.threading.impl.ForkJoinPoolTaskExecutor;
import org.threadlys.threading.impl.ForkJoinPoolWorkerTimeoutController;
import org.threadlys.threading.impl.ThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.RequestScopedThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.ThreadLocalTransferrerRegistryImpl;
import org.threadlys.threading.impl.TransferrableRequestContext;
import org.threadlys.threading.impl.TransferrableSecurityContext;
import org.threadlys.threading.impl.TransferrableThreadLocalPostProcessor;
import org.threadlys.threading.impl.TransferrableThreadScope;

//intentionally NOT annotated with @Configuration to be invisible by classpath-scanning
@Import({ //
        AsyncDataProcessorImpl.class, //
        ConcurrentProcessingFilter.class, //
        ContextSnapshotControllerImpl.class, //
        ForkJoinPoolGuard.class, //
        ForkJoinPoolTaskExecutor.class, //
        ForkJoinPoolWorkerTimeoutController.class, //
        ThreadlyStreamingConfiguration.class, //
        RequestScopedThreadlyStreamingConfiguration.class, //
        StateFetcherFactoryImpl.class, //
        StateFetcherFactoryInternal.class, //
        ThreadLocalMessageDigestImpl.class, //
        ThreadLocalTransferrerRegistryImpl.class, //
        TransferrableApplicationContext.class, //
        TransferrableRequestContext.class, //
        TransferrableSecurityContext.class, //
        TransferrableThreadLocalPostProcessor.class, //
        TransferrableThreadLocalsImpl.class //
})
public class CommonsThreadingSpringConfig {

    public static final String TRANSFERRABLE_THREAD_SCOPE_NAME = "transferrableThreadScope";

    public static final String TRANSFERRABLE_THREAD_SCOPE_PP_NAME = "transferrableThreadScopePP";

    @Bean(TRANSFERRABLE_THREAD_SCOPE_PP_NAME)
    protected BeanFactoryPostProcessor postProcessor() {
        return beanFactory -> {
            var scope = new TransferrableThreadScope();
            beanFactory.registerScope("thread", scope);
            beanFactory.registerScope("request", scope);
            beanFactory.registerSingleton(TRANSFERRABLE_THREAD_SCOPE_NAME, scope);
        };
    }
}
