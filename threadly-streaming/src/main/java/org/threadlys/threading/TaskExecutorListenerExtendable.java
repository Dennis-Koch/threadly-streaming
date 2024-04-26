package org.threadlys.threading;

import org.threadlys.utils.IStateRollback;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface TaskExecutorListenerExtendable {
    IStateRollback registerTaskExecutorListener(TaskExecutorListener listener);
}
