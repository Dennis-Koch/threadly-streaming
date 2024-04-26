package org.threadlys.threading.impl;

import org.threadlys.utils.IStateRollback;

/**
 * Internal interface for the {@link ContextSnapshotImpl} in order to
 * apply/rollback its context to/from the current thread
 *
 * @author Dennis Koch (EXXETA AG)
 */
public interface ContextSnapshotController {

    IStateRollback pushContext(ContextSnapshotIntern contextSnapshot);

    void popContext(ContextSnapshotIntern contextSnapshot);
}
