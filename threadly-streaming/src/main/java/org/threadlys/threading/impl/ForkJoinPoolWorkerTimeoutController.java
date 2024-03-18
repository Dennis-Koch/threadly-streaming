package org.threadlys.threading.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "threadlys.threading.worker-timeout", matchIfMissing = false)
public class ForkJoinPoolWorkerTimeoutController {

    @Autowired
    ForkJoinPoolGuard forkJoinPoolGuard;

    @Scheduled(fixedRateString = "$(threadlys.threading.worker-timeout-check-interval:5000}", initialDelay = 60000)
    public void checkForStaleWorkers() {
        var fjp = forkJoinPoolGuard.getDefaultForkJoinPoolOptional()
                .orElse(null);
        if (fjp == null) {
            // nothing to do
            return;
        }
        var busyThreads = fjp.resolveBusyTimedOutThreads();
        if (busyThreads.isEmpty()) {
            // nothing to do
            return;
        }
        for (var thread : busyThreads) {
            thread.interrupt();
        }
    }
}
