package org.threadlys.state;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class StateFetcherFactoryInternal {

    @Async
    public <S> Future<S> executeRunnableAsync(Supplier<S> runnable) {
        return CompletableFuture.completedFuture(runnable.get());
    }
}
