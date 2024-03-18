package org.threadlys.configuration;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import org.threadlys.threading.impl.TransferrableThreadScope;

import lombok.extern.slf4j.Slf4j;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
@Configuration
@Slf4j
@ConditionalOnProperty(name = "threadlys.threading.transferrable-scope-active", havingValue = "true", matchIfMissing = false)
public class CommonsThreadingAutoConfiguration {
    @Primary
    @Bean(CommonsThreadingSpringConfig.TRANSFERRABLE_THREAD_SCOPE_PP_NAME)
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return beanFactory -> {
            log.debug("TransferrableThreadScope will be registered");
            Scope threadScope = new TransferrableThreadScope();
            beanFactory.registerScope("thread", threadScope);
            beanFactory.registerSingleton(CommonsThreadingSpringConfig.TRANSFERRABLE_THREAD_SCOPE_NAME, threadScope);
        };
    }
}
