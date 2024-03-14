package org.threadlys.threading.test.context;

import java.util.Properties;

import org.threadlys.utils.FutureUtilImpl;
import org.threadlys.utils.ReflectUtilImpl;
import org.threadlys.utils.SneakyThrowUtilImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import org.threadlys.threading.impl.ConcurrentProcessingFilter;
import org.threadlys.threading.impl.ContextSnapshotControllerImpl;
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.threading.impl.ForkJoinPoolTaskExecutor;
import org.threadlys.threading.impl.ThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.RequestScopedThreadlyStreamingConfiguration;
import org.threadlys.threading.impl.ThreadLocalTransferrerRegistryImpl;
import org.threadlys.threading.impl.TransferrableRequestContext;
import org.threadlys.threading.impl.TransferrableSecurityContext;
import org.threadlys.threading.impl.TransferrableThreadLocalPostProcessor;
import org.threadlys.threading.impl.TransferrableThreadLocalsImpl;

@EnableAsync
@Import(value = { ConcurrentProcessingFilter.class, ContextSnapshotControllerImpl.class, ForkJoinPoolGuard.class, ForkJoinPoolTaskExecutor.class, FutureUtilImpl.class, ThreadlyStreamingConfiguration.class,
        ReflectUtilImpl.class, RequestScopedThreadlyStreamingConfiguration.class, SneakyThrowUtilImpl.class, ThreadLocalTransferrerRegistryImpl.class, TransferrableRequestContext.class,
        TransferrableSecurityContext.class, TransferrableThreadLocalPostProcessor.class, TransferrableThreadLocalsImpl.class })

public class ThreadingBeanConfiguration {
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
        final Properties properties = new Properties();

        final PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        configurer.setLocalOverride(true);
        configurer.setProperties(properties);
        return configurer;
    }
}
