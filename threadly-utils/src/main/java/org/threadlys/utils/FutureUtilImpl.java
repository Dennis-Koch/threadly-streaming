package org.threadlys.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.SneakyThrows;

//CHECKSTYLE: IllegalCatch OFF
//CHECKSTYLE: JavadocMethod OFF
//CHECKSTYLE: MagicNumber OFF
@SuppressWarnings({ "checkstyle:IllegalCatch", "checkstyle:JavadocMethod", "checkstyle:MagicNumber" })
@Component
public class FutureUtilImpl implements FutureUtil {
    private Duration waitPeriod = Duration.ofSeconds(30000);

    @Autowired
    private SneakyThrowUtil sneakyThrowUtil;

    @Override
    public Duration remainingDuration(Instant waitTill) {
        return Duration.between(Instant.now(), waitTill);
    }

    @Override
    public <T> T wait(Future<T> future) {
        return wait(future, waitPeriod.toMillis(), TimeUnit.MILLISECONDS);
    }

    @SneakyThrows
    @Override
    public <T> T wait(Future<T> future, long time, TimeUnit timeUnit) {
        return future.get(time, timeUnit);
    }

    @Override
    public <T> T waitTill(Future<T> future, Instant waitTill) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(remainingDuration(waitTill).toMillis(), TimeUnit.MILLISECONDS);
    }

    @SneakyThrows
    @Override
    public <T> T exchangeTill(Exchanger<T> exchanger, T exchange, Instant waitTill) {
        return exchanger.exchange(exchange, remainingDuration(waitTill).toMillis(), TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, F extends Future<?>> List<T> allOf(F... futures) {
        return allOf(Arrays.asList(futures));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, F extends Future<?>> List<T> allOf(Collection<F> futures) {
        if (futures == null) {
            return null;
        }
        return futures.stream().map(future -> {
            Throwable ex = null;
            try {
                return (T) future.get();
            } catch (ExecutionException e) {
                if (e.getMessage() == null) {
                    ex = e.getCause();
                } else {
                    ex = e;
                }
            } catch (Exception e) {
                ex = e;
            }
            throw sneakyThrowUtil.sneakyThrow(ex);
        }).collect(Collectors.toList());
    }
}
