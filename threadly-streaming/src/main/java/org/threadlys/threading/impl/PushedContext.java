package org.threadlys.threading.impl;

import java.util.Map;

import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.utils.IStateRollback;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Internal handle in order to support a reentrant/scoped behavior when using {@link ContextSnapshotFactory#createSnapshot()}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
@RequiredArgsConstructor
public class PushedContext {
    @Getter
    private final ContextSnapshotIntern contextSnapshot;

    @Getter
    private final IStateRollback[] oldValues;

    @Getter
    private final Map<String, Object> oldThreadScopeMap;

    @Getter
    private final TransferrableBeanProcessor oldBeanProcessor;
}
