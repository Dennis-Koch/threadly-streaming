package org.threadlys.threading;

import java.util.List;

/**
 * Beans may implement this interface as an alternative to annotate their
 * thread-local fields with {@link Transferrable}. This is also usable if a
 * beans wants to expose a thread-local instance that is not a directly member
 * of the bean itself (e.g. from a 3rd party library instead)
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface TransferrableThreadLocalProvider {
    List<TransferrableThreadLocal<?>> getTransferrableThreadLocals();
}
