package org.threadlys.threading.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.threadlys.utils.IStateRevert;
import org.threadlys.utils.ListenersMapAdapter;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ThreadLocalTransferrer;
import org.threadlys.threading.ThreadLocalTransferrerExtendable;
import org.threadlys.threading.ThreadLocalTransferrerRegistry;

@Component
public class ThreadLocalTransferrerRegistryImpl implements ThreadLocalTransferrerExtendable, ThreadLocalTransferrerRegistry {
    protected final Map<Class<?>, ThreadLocalTransferrer<?>> beanTypeToTransferrerMap = new ConcurrentHashMap<>();

    @Override
    public List<ThreadLocalTransferrer<?>> getThreadLocalTransferrers(Class<?> beanType) {
        ThreadLocalTransferrer<?> threadLocalTransferrer = beanTypeToTransferrerMap.get(beanType);
        if (threadLocalTransferrer == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(threadLocalTransferrer);
    }

    @Override
    public <T> IStateRevert registerThreadLocalTransferrer(ThreadLocalTransferrer<? super T> transferrer, Class<T> beanType) {
        ListenersMapAdapter.registerListener(transferrer, beanType, beanTypeToTransferrerMap);
        return () -> unregisterThreadLocalTransferrer(transferrer, beanType);
    }

    protected <T> void unregisterThreadLocalTransferrer(ThreadLocalTransferrer<? super T> transferrer, Class<T> beanType) {
        ListenersMapAdapter.unregisterListener(transferrer, beanType, beanTypeToTransferrerMap);
    }
}
