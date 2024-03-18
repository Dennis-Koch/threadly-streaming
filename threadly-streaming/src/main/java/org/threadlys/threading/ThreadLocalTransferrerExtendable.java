package org.threadlys.threading;

import org.threadlys.utils.IStateRollback;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ThreadLocalTransferrerExtendable {
    <T> IStateRollback registerThreadLocalTransferrer(ThreadLocalTransferrer<? super T> transferrer, Class<T> beanType);
}
