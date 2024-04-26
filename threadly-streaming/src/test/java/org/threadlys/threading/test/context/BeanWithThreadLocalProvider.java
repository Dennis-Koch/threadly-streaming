package org.threadlys.threading.test.context;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;
import org.threadlys.threading.TransferrableThreadLocals;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
public class BeanWithThreadLocalProvider implements TransferrableThreadLocalProvider {
    public ThreadLocal<String> lastValueTL = new NamedThreadLocal<>("BeanWithThreadLocalProvider.lastValueTL");

    @Autowired
    protected TransferrableThreadLocals transferrableThreadLocals;

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return Arrays.asList(transferrableThreadLocals.wrap(lastValueTL));
    }
}
