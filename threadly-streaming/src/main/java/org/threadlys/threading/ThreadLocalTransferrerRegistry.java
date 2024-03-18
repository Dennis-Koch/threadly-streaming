package org.threadlys.threading;

import java.util.List;

public interface ThreadLocalTransferrerRegistry {
    List<ThreadLocalTransferrer<?>> getThreadLocalTransferrers(Class<?> beanType);
}
