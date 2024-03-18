package org.threadlys.threading.test.context;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ThreadLocalBean;
import org.threadlys.threading.ThreadLocalScope;
import org.threadlys.threading.Transferrable;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
@ThreadLocalScope
public class BeanWithThreadLocalScope implements DisposableBean, ThreadLocalBean<BeanWithThreadLocalScope> {
    public static final AtomicInteger createCount = new AtomicInteger();

    public static final AtomicInteger destroyCount = new AtomicInteger();

    public static final AtomicInteger customTransferCount = new AtomicInteger();

    @Transferrable
    @Getter
    @Setter
    private String value = "default-initializer";

    @Getter
    @Setter
    private String customValue;

    public BeanWithThreadLocalScope() {
        createCount.incrementAndGet();
    }

    /**
     * This is in order to trick the Spring scoped proxy which would otherwise replace our this pointer at runtime with the proxy pointer. But we want the true "this" pointer for our testing setup
     * here
     *
     * @return An array of length 1 containing the real "this" instance of the current bean and not a scoped proxy instance
     */
    public Object[] getThis() {
        return new Object[] { this };
    }

    @Override
    public void destroy() throws Exception {
        destroyCount.incrementAndGet();
    }

    @Override
    public void fillForkedThreadLocalBean(BeanWithThreadLocalScope targetBean) {
        customTransferCount.incrementAndGet();
    }
}
