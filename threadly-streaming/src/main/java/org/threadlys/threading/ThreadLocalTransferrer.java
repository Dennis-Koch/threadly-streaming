package org.threadlys.threading;

/**
 * Similar to {@link ThreadLocalBean} it allows to customize the thread-local
 * transfer procedure between two thread-local beans. The difference is that
 * this <code>ThreadLocalTransformer</code> is not the bean itself but another
 * bean that customizes the procedure of the target bean without changing the
 * code of the target bean itself. In order to apply your own tranferrer you
 * need to register/unregister it via {@link ThreadLocalTransferrerExtendable}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 * @param <T>
 */
public interface ThreadLocalTransferrer<T> {
    void transferFromMasterToFork(T masterBean, T forkBean);
}
