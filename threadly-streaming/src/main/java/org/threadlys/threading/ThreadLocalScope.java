package org.threadlys.threading;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.annotation.AliasFor;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Inspired by {@link RequestScope}. Beans can annotate themselves with this in
 * order to scope them for each calling thread.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scope("thread")
public @interface ThreadLocalScope {

    /**
     * Alias for {@link Scope#proxyMode}.
     * <p>
     * Defaults to {@link ScopedProxyMode#TARGET_CLASS}.
     */
    @AliasFor(annotation = Scope.class)
    ScopedProxyMode proxyMode() default ScopedProxyMode.TARGET_CLASS;

}
