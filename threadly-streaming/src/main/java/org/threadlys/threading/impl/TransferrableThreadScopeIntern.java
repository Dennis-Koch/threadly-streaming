package org.threadlys.threading.impl;

import java.util.Map;

public interface TransferrableThreadScopeIntern {
    Map<String, Object> getThreadScopeMap();

    Map<String, Object> getAndRemoveThreadScopeMap();

    void setThreadScopeMap(Map<String, Object> threadScopeMap);

    TransferrableBeanProcessor getBeanProcessor();

    void setBeanPostProcessor(TransferrableBeanProcessor beanProcessor);
}
