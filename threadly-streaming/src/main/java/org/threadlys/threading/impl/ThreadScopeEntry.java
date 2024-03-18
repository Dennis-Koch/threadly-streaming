package org.threadlys.threading.impl;

import java.lang.reflect.Field;

import org.threadlys.threading.ContextSnapshotFactory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Internal handle in order to support a reentrant/scoped behavior when using
 * {@link ContextSnapshotFactory#createSnapshot()}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
@RequiredArgsConstructor
public class ThreadScopeEntry {
    @Getter
    private final Object originalBean;

    @Getter
    private final Field[] fields;

    @Getter
    private final Object[] originalValues;
}
