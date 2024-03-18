package org.threadlys.threading.impl;

import java.util.Arrays;
import java.util.List;

import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.ReflectUtil;
import org.threadlys.utils.StateRollback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import org.threadlys.threading.TransferrableThreadLocal;
import org.threadlys.threading.TransferrableThreadLocalProvider;
import org.threadlys.threading.TransferrableThreadLocals;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "PMD.CompareObjectsWithEquals", "checkstyle:JavadocMethod" })
@Component
public class TransferrableSecurityContext implements TransferrableThreadLocalProvider {
    public static class SecurityContextTransferrableThreadLocal implements TransferrableThreadLocal<Authentication> {
        @Override
        public Authentication get() {
            return SecurityContextHolder.getContext()
                    .getAuthentication();
        }

        @Override
        public IStateRollback setForFork(Authentication newForkedValue, Authentication oldForkedValue) {
            SecurityContextHolder.getContext()
                    .setAuthentication(newForkedValue);
            if (oldForkedValue == null) {
                return () -> SecurityContextHolder.clearContext();
            } else {
                return () -> SecurityContextHolder.getContext()
                        .setAuthentication(oldForkedValue);
            }
        }

        @Override
        public String toString() {
            return "Transferrable-" + SecurityContextHolder.class.getName();
        }
    }

    public static IStateRollback pushAuthentication(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication oldAuthentication = context.getAuthentication();
        if (oldAuthentication == authentication) {
            // nothing to do
            return StateRollback.empty();
        }
        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
        return () -> SecurityContextHolder.getContext()
                .setAuthentication(oldAuthentication);
    }

    @Autowired
    protected ReflectUtil reflectUtil;

    @Autowired
    protected TransferrableThreadLocals transferrableThreadLocals;

    @Override
    public List<TransferrableThreadLocal<?>> getTransferrableThreadLocals() {
        return Arrays.asList(new SecurityContextTransferrableThreadLocal());
    }
}
