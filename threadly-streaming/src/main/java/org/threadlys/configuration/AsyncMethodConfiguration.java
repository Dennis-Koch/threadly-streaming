package org.threadlys.configuration;

import org.threadlys.threading.impl.ForkJoinPoolTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Note that {@link EnableAsync} here is meant to activate {@link ForkJoinPoolTaskExecutor}
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "threadlys.threading.task-executor-active", havingValue = "true")
public class AsyncMethodConfiguration {
}
