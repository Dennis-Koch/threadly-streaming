package org.threadlys.threading.impl;

import org.threadlys.utils.StateRevert;

/**
 * Internal interface for the {@link ContextSnapshotImpl} in order to
 * apply/revert its context to/from the current thread
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface ContextSnapshotController {

    StateRevert pushContext(ContextSnapshotIntern contextSnapshot);

    void popContext(ContextSnapshotIntern contextSnapshot);
}
