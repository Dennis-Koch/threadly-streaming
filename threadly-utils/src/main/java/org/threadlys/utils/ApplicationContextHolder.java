package org.threadlys.utils;

import org.springframework.context.ApplicationContext;
import org.springframework.core.NamedThreadLocal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

//CHECKSTYLE: ConstantName OFF
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationContextHolder {

    private static final ThreadLocal<ApplicationContext> applicationContextTL = new NamedThreadLocal<>("ThreadLocalApplicationContextHolderStrategy.applicationContextTL");

    public static ApplicationContext getContext() {
        return applicationContextTL.get();
    }

    public static void setContext(ApplicationContext context) {
        if (context != null) {
            applicationContextTL.set(context);    
        } else {
            applicationContextTL.remove();
        }
    }
    
    public static IStateRollback pushApplicationContext(ApplicationContext applicationContext) {
        var oldApplicationContext = ApplicationContextHolder.getContext();
        if (oldApplicationContext == applicationContext) {
            // nothing to do
            return StateRollback.empty();
        }
        ApplicationContextHolder.setContext(applicationContext);
        return () -> ApplicationContextHolder.setContext(oldApplicationContext);
    }
}
