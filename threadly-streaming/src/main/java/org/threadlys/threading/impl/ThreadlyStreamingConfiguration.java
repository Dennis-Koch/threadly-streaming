package org.threadlys.threading.impl;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;
import org.threadlys.threading.TransferrableThreadLocals;

@Component
public class ThreadlyStreamingConfiguration implements InitializingBean, TransferrableThreadLocalProvider {

    @Autowired
    RequestScopedThreadlyStreamingConfiguration requestScopedThreadlyStreamingConfiguration;

    @Autowired
    TransferrableThreadLocals transferrableThreadLocals;

    protected final ThreadingConfigurationValues initialThreadlyStreamingConfiguration = new ThreadingConfigurationValues();

    protected final ThreadLocal<ThreadingConfigurationValues> meHomeThreadingConfigurationTL = new NamedThreadLocal<>("ThreadlyStreamingConfiguration.meHomeThreadingConfigurationTL");

    @Value("${threadlys.threading.filter-active:false}")
    protected void setInitialFilterActive(Boolean filterActive) {
        initialThreadlyStreamingConfiguration.setFilterActive(filterActive);
    }

    @Value("${threadlys.threading.task-executor-active:false}")
    protected void setInitialTaskExecutorActive(Boolean taskExecutorActive) {
        initialThreadlyStreamingConfiguration.setTaskExecutorActive(taskExecutorActive);
    }

    @Value("${threadlys.threading.parallel-active:true}")
    protected void setInitialParallelActive(Boolean parallelActive) {
        initialThreadlyStreamingConfiguration.setParallelActive(parallelActive);
    }

    @Value("${threadlys.threading.pool-per-request:false}")
    protected void setPoolPerRequest(Boolean poolPerRequest) {
        initialThreadlyStreamingConfiguration.setPoolPerRequest(poolPerRequest);
    }

    @Value("${threadlys.threading.maximumPoolSize:0x7fff}")
    protected void setInitialMaximumPoolSize(Integer maximumPoolSize) {
        initialThreadlyStreamingConfiguration.setMaximumPoolSize(maximumPoolSize);
    }

    @Value("${threadlys.threading.poolSize:}")
    protected void setInitialPoolSize(Integer poolSize) {
        initialThreadlyStreamingConfiguration.setPoolSize(poolSize);
    }

    @Value("${threadlys.threading.timeout:PT10M}")
    protected void setInitialTimeout(Duration timeout) {
        initialThreadlyStreamingConfiguration.setTimeout(timeout);
    }

    @Value("${threadlys.threading.worker-timeout:}")
    protected void setInitialWorkerTimeout(Duration workerTimeout) {
        initialThreadlyStreamingConfiguration.setWorkerTimeout(workerTimeout);
    }

    @Value("${threadlys.threading.gracePeriod:PT0.5S}")
    protected void setInitialGracePeriod(Duration gracePeriod) {
        initialThreadlyStreamingConfiguration.setGracePeriod(gracePeriod);
    }

    @Value("${threadlys.threading.header-permitted:false}")
    protected void setInitiaThreadlyStreamingHeaderPermitted(Boolean meHomeThreadingHeaderPermitted) {
        initialThreadlyStreamingConfiguration.setThreadlyStreamingHeaderPermitted(meHomeThreadingHeaderPermitted);
    }

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return List.of(transferrableThreadLocals.wrap(meHomeThreadingConfigurationTL));
    }

    protected ThreadingConfigurationValues resolveCurrentThreadlyStreamingConfiguration() {
        try {
            return requestScopedThreadlyStreamingConfiguration.getCurrentThreadlyStreamingConfiguration();
        } catch (ScopeNotActiveException e) {
            return handleNotInAWebEnvironment();
        } catch (IllegalStateException e) {
            if (!"No Scope registered for scope name 'request'".equals(e.getMessage())) {
                throw e;
            }
            return handleNotInAWebEnvironment();
        }
    }

    protected ThreadingConfigurationValues handleNotInAWebEnvironment() {
        // we are not in a web environment
        ThreadingConfigurationValues meHomeThreadingConfiguration = meHomeThreadingConfigurationTL.get();
        if (meHomeThreadingConfiguration == null) {
            meHomeThreadingConfiguration = new ThreadingConfigurationValues();
            meHomeThreadingConfigurationTL.set(meHomeThreadingConfiguration);
        }
        return meHomeThreadingConfiguration;
    }

    protected <T> T resolveConfigurationValue(Function<ThreadingConfigurationValues, T> resolver) {
        return Optional.of(resolveCurrentThreadlyStreamingConfiguration())
                .map(resolver)
                .orElseGet(() -> resolver.apply(initialThreadlyStreamingConfiguration));
    }

    public boolean isFilterActive() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getFilterActive);
    }

    public boolean isTaskExecutorActive() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getTaskExecutorActive);
    }

    public boolean isParallelActive() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getParallelActive);
    }

    public Integer getMaximumPoolSize() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getMaximumPoolSize);
    }

    public Boolean getPoolPerRequest() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getPoolPerRequest);
    }

    public Integer getPoolSize() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getPoolSize);
    }

    public Duration getTimeout() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getTimeout);
    }

    public Duration getGracePeriod() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getGracePeriod);
    }

    public Duration getWorkerTimeout() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getWorkerTimeout);
    }

    public boolean isThreadlyStreamingHeaderPermitted() {
        return resolveConfigurationValue(ThreadingConfigurationValues::getThreadlyStreamingHeaderPermitted);
    }

    public void setFilterActive(Boolean filterActive) {
        resolveCurrentThreadlyStreamingConfiguration().setFilterActive(filterActive);
    }

    public void setTaskExecutorActive(Boolean taskExecutorActive) {
        resolveCurrentThreadlyStreamingConfiguration().setTaskExecutorActive(taskExecutorActive);
    }

    public void setParallelActive(Boolean parallelActive) {
        resolveCurrentThreadlyStreamingConfiguration().setParallelActive(parallelActive);
    }

    public void setMaximumPoolSize(Integer maximumPoolSize) {
        resolveCurrentThreadlyStreamingConfiguration().setMaximumPoolSize(maximumPoolSize);
    }

    public void setPoolSize(Integer poolSize) {
        resolveCurrentThreadlyStreamingConfiguration().setPoolSize(poolSize);
    }

    public void setTimeout(Duration timeout) {
        resolveCurrentThreadlyStreamingConfiguration().setTimeout(timeout);
    }

    public void setWorkerTimeout(Duration workerTimeout) {
        resolveCurrentThreadlyStreamingConfiguration().setWorkerTimeout(workerTimeout);
    }

    public void setGracePeriod(Duration gracePeriod) {
        resolveCurrentThreadlyStreamingConfiguration().setGracePeriod(gracePeriod);
    }

    public void applyThreadlyStreamingConfiguration(ThreadingConfigurationValues threadingConfigurationValues) {
        try {
            requestScopedThreadlyStreamingConfiguration.applyCurrentThreadlyStreamingConfiguration(threadingConfigurationValues);
        } catch (IllegalStateException e) {
            if (!"No Scope registered for scope name 'request'".equals(e.getMessage())) {
                throw e;
            }
            // we are not in a web environment
            meHomeThreadingConfigurationTL.set(threadingConfigurationValues);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (initialThreadlyStreamingConfiguration.getPoolSize() == null) {
            /**
             * allow up to 4 workers per CPU. this is because our basic assumption is that most of our request processing is backend-roundtrip-limited (=latency) and not CPU or memory limited
             */
            // CHECKSTYLE: MagicNumber OFF
            initialThreadlyStreamingConfiguration.setPoolSize(Math.max(Runtime.getRuntime()
                    .availableProcessors() - 1, 1) * 8);
            // CHECKSTYLE: MagicNumber ON
        }
    }
}
