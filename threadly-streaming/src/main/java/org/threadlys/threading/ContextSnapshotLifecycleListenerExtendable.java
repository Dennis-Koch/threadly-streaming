package org.threadlys.threading;

import org.threadlys.utils.IStateRollback;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ContextSnapshotLifecycleListenerExtendable {

    IStateRollback registerContextSnapshotLifecycleListener(ContextSnapshotLifecycleListener listener);
}
