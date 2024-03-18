package org.threadlys.threading.impl;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.threadlys.utils.ReflectUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import org.threadlys.threading.ThreadLocalBean;
import org.threadlys.threading.Transferrable;
import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;
import org.threadlys.threading.TransferrableThreadLocals;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

// CHECKSTYLE: MagicNumber OFF
@SuppressWarnings({ "PMD.AssignmentInOperand", "PMD.AvoidUsingVolatile", "PMD.CompareObjectsWithEquals", "PMD.DoNotUseThreads", "checkstyle:MagicNumber", "PMD.OptimizableToArrayCall" })
@Component
@Slf4j
public class TransferrableThreadLocalPostProcessor implements DestructionAwareBeanPostProcessor, DisposableBean, ApplicationContextAware {
    protected final Object syncObject = new Object();

    protected WeakHashMap<Object, List<TransferrableThreadLocal<?>>> beanToThreadLocalsMap = new WeakHashMap<>();

    protected WeakHashMap<TransferrableThreadLocal<?>, AtomicInteger> threadLocalToUsageCounterMap = new WeakHashMap<>();

    protected Reference<TransferrableThreadLocal<?>[]> threadLocalsRef;

    protected volatile int monitoredThreadLocalCount;

    protected Duration logInterval = Duration.ofSeconds(10);

    protected Instant nextLogging = Instant.now().plus(logInterval);

    // because we are in a post processor we "inject" our beans lazily
    @Getter(lazy = true)
    private final ReflectUtil reflectUtil = resolveReflectUtil();

    // because we are in a post processor we "inject" our beans lazily
    @Getter(lazy = true)
    private final TransferrableThreadLocals transferrableThreadLocals = resolveTransferrableThreadLocals();

    private ApplicationContext applicationContext;

    private ReflectUtil resolveReflectUtil() {
        return applicationContext.getBean(ReflectUtil.class);
    }

    private TransferrableThreadLocals resolveTransferrableThreadLocals() {
        return applicationContext.getBean(TransferrableThreadLocals.class);
    }

    @Override
    public void destroy() throws Exception {
        synchronized (syncObject) {
            beanToThreadLocalsMap = null;
            threadLocalToUsageCounterMap = null;
            threadLocalsRef = null;
        }
    }

    public void disposeAllForkedThreadLocalBeans(Map<String, Object> threadScopeMap, Map<String, Object> recentThreadScopeMap) {
        if (threadScopeMap == null) {
            return;
        }
        AutowireCapableBeanFactory beanFactory = null;
        var iter = threadScopeMap.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var currentValue = entry.getValue();
            if (currentValue == null) {
                continue;
            }
            var recentValue = recentThreadScopeMap != null ? recentThreadScopeMap.get(entry.getKey()) : null;
            if (recentValue == currentValue) {
                // nothing to do
                continue;
            }
            if (beanFactory == null) {
                beanFactory = applicationContext.getAutowireCapableBeanFactory();
            }
            beanFactory.destroyBean(currentValue);
        }
    }

    public TransferrableThreadLocal<?>[] getOrCreateCachedThreadLocals() {
        synchronized (syncObject) {
            if (threadLocalToUsageCounterMap == null) {
                return ContextSnapshotControllerImpl.EMPTY_THREAD_LOCALS;
            }
            var threadLocals = threadLocalsRef != null ? threadLocalsRef.get() : null;
            if (threadLocals == null) {
                threadLocals = threadLocalToUsageCounterMap.keySet().toArray(new TransferrableThreadLocal<?>[threadLocalToUsageCounterMap.size()]);
                threadLocalsRef = new SoftReference<>(threadLocals);
            }
            return threadLocals;
        }
    }

    protected List<TransferrableThreadLocal<?>> resolveThreadLocals(Object bean) {
        if (bean == null) {
            return null;
        }
        if (bean instanceof TransferrableThreadLocalProvider) {
            return resolveThreadLocals((TransferrableThreadLocalProvider) bean);
        }
        var transferrableThreadLocalList = resolveThreadLocalFields(bean);
        if (transferrableThreadLocalList == null && bean instanceof ThreadLocalBean) {
            return Collections.emptyList();
        }
        return transferrableThreadLocalList != null ? transferrableThreadLocalList.stream()//
                        .filter(Objects::nonNull)//
                        .distinct()//
                        .collect(Collectors.toList()) : null;
    }

    protected List<TransferrableThreadLocal<?>> resolveThreadLocals(TransferrableThreadLocalProvider bean) {
        var transferrableThreadLocalList = bean.getTransferrableThreadLocals();
        if (transferrableThreadLocalList == null || transferrableThreadLocalList.isEmpty()) {
            return Collections.emptyList();
        }
        return transferrableThreadLocalList.stream()//
                        .filter(Objects::nonNull)//
                        .distinct()//
                        .collect(Collectors.toList());
    }

    protected List<TransferrableThreadLocal<?>> resolveThreadLocalFields(Object bean) {
        var reflectUtil = getReflectUtil();
        var transferrableThreadLocals = getTransferrableThreadLocals();
        var fields = reflectUtil.getAllDeclaredFields(bean.getClass());
        List<TransferrableThreadLocal<?>> transferrableThreadLocalList = null;
        for (Field field : fields) {
            if (field.getAnnotation(Transferrable.class) == null) {
                continue;
            }
            if (ThreadLocal.class.isAssignableFrom(field.getType())) {
                if (transferrableThreadLocalList == null) {
                    transferrableThreadLocalList = new ArrayList<>();
                }
                ThreadLocal<?> threadLocal = reflectUtil.getFieldValue(field, bean);
                if (threadLocal != null) {
                    transferrableThreadLocalList.add(transferrableThreadLocals.wrap(threadLocal));
                }
            }
        }
        return transferrableThreadLocalList;
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        var threadLocals = resolveThreadLocals(bean);
        return threadLocals != null && !threadLocals.isEmpty();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        var threadLocals = resolveThreadLocals(bean);
        if (threadLocals == null) {
            return bean;
        }
        synchronized (syncObject) {
            if (beanToThreadLocalsMap.putIfAbsent(bean, threadLocals) != null) {
                // for some reason already handled
                return bean;
            }
            for (TransferrableThreadLocal<?> threadLocal : threadLocals) {
                var usageCounter = threadLocalToUsageCounterMap.get(threadLocal);
                if (usageCounter == null) {
                    usageCounter = new AtomicInteger(1);
                    threadLocalToUsageCounterMap.put(threadLocal, usageCounter);
                    threadLocalsRef = null;
                } else {
                    usageCounter.incrementAndGet();
                }
            }
            monitoredThreadLocalCount += threadLocals.size();
            logMonitoring(() -> new Object[] { monitoredThreadLocalCount, threadLocalToUsageCounterMap.size(), beanToThreadLocalsMap.size() });
        }
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (bean == null || !beanToThreadLocalsMap.containsKey(bean)) {
            return;
        }
        synchronized (syncObject) {
            var threadLocals = beanToThreadLocalsMap.remove(bean);
            if (threadLocals == null) {
                return;
            }
            for (TransferrableThreadLocal<?> threadLocal : threadLocals) {
                var usageCounter = threadLocalToUsageCounterMap.get(threadLocal);
                if (usageCounter.decrementAndGet() == 0) {
                    threadLocalToUsageCounterMap.remove(threadLocal);
                    threadLocalsRef = null;
                }
            }
            monitoredThreadLocalCount -= threadLocals.size();
            logMonitoring(() -> new Object[] { monitoredThreadLocalCount, threadLocalToUsageCounterMap.size(), beanToThreadLocalsMap.size() });
        }
    }

    protected void logMonitoring(Supplier<Object[]> dataSupplier) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (nextLogging.isAfter(Instant.now())) {
            return;
        }
        var data = dataSupplier.get();
        if (log.isDebugEnabled()) {
            log.debug("Monitoring " + data[0] + " (distinct: " + data[1] + ") thread-locals provided by " + data[2] + " beans");
        }
        nextLogging = Instant.now().plus(logInterval);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
