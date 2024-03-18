package org.threadlys.threading;

/**
 * Allows to customize the logic that fills transferrable fields between two
 * thread-local beans (= a bean annotated with {@link ThreadLocalScope}. Note
 * that this callback is invoked on the original thread-local bean. The
 * invocation itself is also done from the forked thread - so it is important to
 * make the implementation of this method thread-safe related to "this".<br>
 * <br>
 * NOTE: Implementing this interface without using the {@link ThreadLocalScope}
 * annotation on the same bean class does not have any effect.<br>
 * <br>
 * Usage Example:<br>
 * <br>
 * <code>
 * &#64;ThreadLocalScope<br>
 * public class MyBean implements ThreadLocalBean<MyBean> {<br>
 * &nbsp;&nbsp;// this makes sure that the "myValue" property is copied from a thread-local instance<br>
 * &nbsp;&nbsp;// of MyBean of the master thread to each thread-local instance of MyBean of forked threads<br>
 * &nbsp;&nbsp;private StringBuilder myValue = new StringBuilder();<br>
 * &nbsp;&nbsp;@Override<br>
 * &nbsp;&nbsp;public void fillThreadLocalBean(BeanWithThreadLocalScope targetBean) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;targetBean.myValue = new StringBuilder(myValue)<br>
 * &nbsp;&nbsp;}<br>
 * }</br>
 * </code>
 *
 * @author Dennis Koch (EXXETA AG)
 *
 * @param <T>
 *                The bean type implementing this interface
 */
public interface ThreadLocalBean<T> {
    /**
     * Note that this callback is
     * <ul>
     * <li>invoked from the forked thread</li>
     * <li>on the original thread-local bean (which is owned by the master
     * thread</li>
     * <li>passing the forked thread-local bean as an argument that needs to receive
     * the transferred properties.</li>
     * </ul>
     * As a result it is important to make the implementation of this method
     * thread-safe/idempotent related to "this" = the original thread-local
     * bean.<br>
     * <br>
     * In simple scenarios there is no need to implement this interface: Annotating
     * all relevant fields with {@link Transferrable} may be sufficient for most
     * cases.
     *
     * @param forkedBean
     *                       Bean instance that needs to receive customized
     *                       transferred properties
     */
    void fillForkedThreadLocalBean(T forkedBean);
}
