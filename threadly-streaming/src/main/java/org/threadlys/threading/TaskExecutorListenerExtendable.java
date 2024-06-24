package org.threadlys.threading;

import org.threadlys.utils.IStateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface TaskExecutorListenerExtendable {
    IStateRevert registerTaskExecutorListener(TaskExecutorListener listener);
}
