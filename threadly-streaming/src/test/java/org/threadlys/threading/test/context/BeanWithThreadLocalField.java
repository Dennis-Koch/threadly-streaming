package org.threadlys.threading.test.context;

import org.springframework.core.NamedThreadLocal;
import org.springframework.stereotype.Component;

import org.threadlys.threading.Transferrable;

/**
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Component
public class BeanWithThreadLocalField {
    @Transferrable
    public ThreadLocal<String> lastValueTL = new NamedThreadLocal<>("BeanWithThreadLocalField.lastValueTL");
}
