package org.threadlys.threading.impl;

import org.threadlys.utils.IStateRevert;

/**
 * Internal interface for the {@link ContextSnapshotImpl} in order to
 * apply/revert its context to/from the current thread
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface ContextSnapshotController {

    IStateRevert pushContext(ContextSnapshotIntern contextSnapshot);

    void popContext(ContextSnapshotIntern contextSnapshot);
}
