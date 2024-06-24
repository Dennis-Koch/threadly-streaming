package org.threadlys.threading;

import org.threadlys.utils.StateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ContextSnapshotLifecycleListenerExtendable {

    StateRevert registerContextSnapshotLifecycleListener(ContextSnapshotLifecycleListener listener);
}
