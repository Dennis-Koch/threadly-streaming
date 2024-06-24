package org.threadlys.threading;

import org.threadlys.utils.IStateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ThreadLocalTransferrerExtendable {
    <T> IStateRevert registerThreadLocalTransferrer(ThreadLocalTransferrer<? super T> transferrer, Class<T> beanType);
}
