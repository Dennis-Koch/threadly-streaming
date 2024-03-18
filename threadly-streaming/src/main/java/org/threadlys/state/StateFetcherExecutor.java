package org.threadlys.state;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Simplified version of {@link ExecutorService} just for the needs of {@link StateFetcher implementation}
 */
@FunctionalInterface
public interface StateFetcherExecutor {
    <T> Future<T> submit(Callable<T> task);
}
