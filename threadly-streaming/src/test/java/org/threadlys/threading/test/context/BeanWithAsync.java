package org.threadlys.threading.test.context;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import org.threadlys.streams.CheckedSupplier;

import lombok.SneakyThrows;

@Component
public class BeanWithAsync {
    private final Map<Thread, Integer> invocationCount = new ConcurrentHashMap<>();

    @Autowired
    private BeanWithThreadLocalScope beanWithThreadLocalScope;

    public Map<Thread, Integer> getInvocationCount() {
        return invocationCount;
    }

    @Async
    public Future<String> getSomethingAsync(String value) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        invocationCount.compute(Thread.currentThread(), (thread, existingValue) -> existingValue == null ? 1 : existingValue + 1);
        String tlValue = beanWithThreadLocalScope.getValue();
        return CompletableFuture.completedFuture(tlValue + "#" + value);
    }

    @Async
    public CompletableFuture<String> getSomethingAsyncCompletable(String value) {
        return (CompletableFuture<String>) getSomethingAsync(value);
    }

    @SneakyThrows
    @Async
    public <T> CompletableFuture<T> invokeSupplierSpringAsync(CheckedSupplier<T> supplier) {
        return CompletableFuture.completedFuture(supplier.get());
    }
}
