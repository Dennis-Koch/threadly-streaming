package org.threadlys.threading;

import org.threadlys.utils.StateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface TaskExecutorListenerExtendable {
    StateRevert registerTaskExecutorListener(TaskExecutorListener listener);
}
