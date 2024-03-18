package org.threadlys.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface FutureUtil {

    Duration remainingDuration(Instant waitTill);

    <T> T wait(Future<T> future);

    <T> T wait(Future<T> future, long time, TimeUnit timeUnit);

    <T> T waitTill(Future<T> future, Instant waitTill) throws InterruptedException, ExecutionException, TimeoutException;

    <T> T exchangeTill(Exchanger<T> exchanger, T exchange, Instant waitTill);

    @SuppressWarnings("unchecked")
    <T, F extends Future<?>> List<T> allOf(F... futures);

    <T, F extends Future<?>> List<T> allOf(Collection<F> futures);
}
