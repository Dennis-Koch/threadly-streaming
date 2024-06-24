package org.threadlys.threading;

import org.threadlys.utils.IStateRevert;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public interface ContextSnapshotLifecycleListenerExtendable {

    IStateRevert registerContextSnapshotLifecycleListener(ContextSnapshotLifecycleListener listener);
}
