package org.threadlys.threading.test.context;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import org.threadlys.threading.ThreadLocalBean;
import org.threadlys.threading.Transferrable;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
@RequestScope
public class BeanWithRequestScope implements DisposableBean, ThreadLocalBean<BeanWithRequestScope> {
    public static AtomicInteger destroyCount = new AtomicInteger();

    public static AtomicInteger customTransferCount = new AtomicInteger();

    @Transferrable
    @Getter
    @Setter
    private String value;

    public Object[] getThis() {
        return new Object[] { this };
    }

    @Override
    public void destroy() throws Exception {
        destroyCount.incrementAndGet();
    }

    @Override
    public void fillForkedThreadLocalBean(BeanWithRequestScope targetBean) {
        customTransferCount.incrementAndGet();
    }
}
