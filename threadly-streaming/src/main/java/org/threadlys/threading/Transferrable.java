package org.threadlys.threading;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Beans may annotate their thread-local fields with this in order to let them
 * automatically handled when it comes to the usage of {@link ContextSnapshot}
 * in order to prepare thread-local fields in multi-threading scenarios.<br>
 * <br>
 * Special handling is done when a bean is annotated with
 * {@link ThreadLocalScope} and then one or more common fields (e.g. of type
 * {@link String} or {@link int} is annotated with {@link Transferrable}: In
 * such cases the thread-local bean is a distinct instance in each thread but
 * the annotated fields are automatically synced from the original to the each
 * forked bean<br>
 * <br>
 * Usage Example:<br>
 * <br>
 * <code>
 * &#64;ThreadLocalScope<br>
 * public class MyBean {<br>
 * &nbsp;&nbsp;// this makes sure that the "myValue" property is copied from a thread-local instance<br>
 * &nbsp;&nbsp;// of MyBean of the master thread to each thread-local instance of MyBean of forked threads<br>
 * &nbsp;&nbsp;@Transferrable<br>
 * &nbsp;&nbsp;private String myValue;<br>
 * }</br>
 * </code><br>
 * <br>
 * NOTE: Be aware of potential race-conditions - the value of a transferrable
 * field and the algorithm in the fork/join scenario working with that value
 * must be safe against race conditions. The transferrable feature only takes
 * care of granting access to the same value from the main thread to the worker
 * threads - nothing more.<br<br>
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Transferrable {
}
