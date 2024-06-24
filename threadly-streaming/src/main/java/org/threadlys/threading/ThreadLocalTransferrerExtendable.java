package org.threadlys.threading;

import org.threadlys.utils.StateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ThreadLocalTransferrerExtendable {
    <T> StateRevert registerThreadLocalTransferrer(ThreadLocalTransferrer<? super T> transferrer, Class<T> beanType);
}
