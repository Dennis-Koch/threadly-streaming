package org.threadlys.threading.test.context;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.threading.Transferrable;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
public class TestServiceImpl implements TestService {
    @Transferrable
    public ThreadLocal<String> lastValueTL = new NamedThreadLocal<>("TestServiceImpl.lastValueTL");

    public Map<Thread, Integer> invocationCount = new ConcurrentHashMap<>();

    @Override
    public int testOperation(String arg) {
        // force some longer calculation to increase the chances for multiple
        // workers to pickup items on the queue
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // intended blank
        }
        lastValueTL.set(arg);
        invocationCount.compute(Thread.currentThread(), (thread, existingValue) -> existingValue == null ? 1 : existingValue + 1);
        return arg != null ? arg.length() : 0;
    }

    @Override
    public Collection<Integer> testOperationBatch(Collection<String> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        return items.parallelStream().map(item -> testOperation(item)).collect(Collectors.toList());
    }

    @Override
    public boolean isClear() {
        return lastValueTL.get() == null;
    }
}
