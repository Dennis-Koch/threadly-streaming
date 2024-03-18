package org.threadlys.threading.impl;

import java.util.Arrays;
import java.util.List;

import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.StateRollback;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "PMD.CompareObjectsWithEquals", "checkstyle:JavadocMethod" })
@Component
public class TransferrableRequestContext implements TransferrableThreadLocalProvider {
    public static class RequestContextHolderTransferrableThreadLocal implements TransferrableThreadLocal<RequestAttributes> {
        @Override
        public RequestAttributes get() {
            return RequestContextHolder.getRequestAttributes();
        }

        @Override
        public IStateRollback setForFork(RequestAttributes newForkedValue, RequestAttributes oldForkedValue) {
            RequestContextHolder.setRequestAttributes(newForkedValue, false);
            return () -> RequestContextHolder.setRequestAttributes(oldForkedValue, false);
        }

        @Override
        public String toString() {
            return "Transferrable-" + RequestContextHolder.class.getName();
        }
    }

    public static IStateRollback pushRequestAttributes(RequestAttributes requestAttributes) {
        RequestAttributes oldRequestAttributes = RequestContextHolder.getRequestAttributes();
        if (oldRequestAttributes == requestAttributes) {
            // nothing to do
            return StateRollback.empty();
        }
        RequestContextHolder.setRequestAttributes(requestAttributes);
        return () -> RequestContextHolder.setRequestAttributes(oldRequestAttributes);
    }

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return Arrays.asList(new RequestContextHolderTransferrableThreadLocal());
    }
}
