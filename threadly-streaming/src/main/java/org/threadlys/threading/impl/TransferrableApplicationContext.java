package org.threadlys.threading.impl;

import java.util.Collections;
import java.util.List;

import org.threadlys.utils.ApplicationContextHolder;
import org.threadlys.utils.StateRevert;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;

@SuppressWarnings("PMD.CompareObjectsWithEquals")
@Component
public class TransferrableApplicationContext implements TransferrableThreadLocalProvider {

    public static class ApplicationContextHolderThreadLocal implements TransferrableThreadLocal<ApplicationContext> {

        @Override
        public ApplicationContext get() {
            return ApplicationContextHolder.getContext();
        }

        @Override
        public StateRevert setForFork(ApplicationContext newForkedValue, ApplicationContext oldForkedValue) {
            ApplicationContextHolder.setContext(newForkedValue);
            return () -> ApplicationContextHolder.setContext(oldForkedValue);
        }

        @Override
        public String toString() {
            return "Transferrable-" + ApplicationContextHolder.class.getName();
        }
    }

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return Collections.singletonList(new ApplicationContextHolderThreadLocal());
    }
}
