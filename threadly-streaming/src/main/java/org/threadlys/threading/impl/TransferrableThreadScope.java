package org.threadlys.threading.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.core.NamedThreadLocal;
import org.springframework.lang.Nullable;

import lombok.extern.slf4j.Slf4j;

/**
 * This class is copied from {@link SimpleThreadScope} with the difference that the {@link #beanPostProcessorTL} field is additionally allows to intercept the thread-local instantiation of a bean
 */
@Slf4j
public class TransferrableThreadScope implements Scope, TransferrableThreadScopeIntern {
    private final ThreadLocal<Map<String, Object>> threadScopeTL = new NamedThreadLocal<>("TransferrableThreadScope.threadScopeTL");

    private final ThreadLocal<TransferrableBeanProcessor> beanProcessorTL = new NamedThreadLocal<>("TransferrableThreadScope.beanProcessorTL");

    @Override
    public Map<String, Object> getThreadScopeMap() {
        return threadScopeTL.get();
    }

    @Override
    public Map<String, Object> getAndRemoveThreadScopeMap() {
        var threadScopeMap = threadScopeTL.get();
        if (threadScopeMap != null) {
            log.debug("identity=TS{} - dropped thread-scoped state", System.identityHashCode(threadScopeMap));
            threadScopeTL.remove();
        }
        return threadScopeMap;
    }

    @Override
    public void setThreadScopeMap(Map<String, Object> threadScopeMap) {
        if (threadScopeMap != null) {
            log.debug("identity=TS{} - activated/restored thread-scoped state", System.identityHashCode(threadScopeMap));
            threadScopeTL.set(threadScopeMap);
        } else {
            log.debug("Cleared threadScope state for current thread");
            threadScopeTL.remove();
        }
    }

    @Override
    public TransferrableBeanProcessor getBeanProcessor() {
        return beanProcessorTL.get();
    }

    @Override
    public void setBeanPostProcessor(TransferrableBeanProcessor beanProcessor) {
        if (beanProcessor == null) {
            beanProcessorTL.remove();
        } else {
            beanProcessorTL.set(beanProcessor);
        }
    }

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        var threadScopeMap = threadScopeTL.get();
        if (threadScopeMap == null) {
            threadScopeMap = new HashMap<>();
            threadScopeTL.set(threadScopeMap);
        }
        var bean = threadScopeMap.get(name);
        if (bean != null) {
            log.debug("identity=TS{} - reuse existing bean '{}@B{}'", System.identityHashCode(threadScopeMap), name, System.identityHashCode(bean));
            return bean;
        }
        var newBean = objectFactory.getObject();
        threadScopeMap.put(name, newBean);
        var beanProcessor = beanProcessorTL.get();
        log.debug("identity=TS{} - create new instance for bean '{}@B{}', transferrableBeanProcessor={}", System.identityHashCode(threadScopeMap), name, System.identityHashCode(newBean),
                beanProcessor);
        if (beanProcessor != null) {
            newBean = beanProcessor.processCreation(name, newBean);
        }
        return newBean;
    }

    @Override
    @Nullable
    public Object remove(String name) {
        var scope = threadScopeTL.get();
        if (scope == null) {
            return null;
        }
        var removedBean = scope.remove(name);
        if (scope.isEmpty()) {
            threadScopeTL.remove();
        }
        return removedBean;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // intended blank
    }

    @Override
    @Nullable
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return Thread.currentThread()
                .getName();
    }
}
