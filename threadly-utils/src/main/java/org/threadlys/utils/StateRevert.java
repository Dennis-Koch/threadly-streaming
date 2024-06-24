package org.threadlys.utils;

/**
 * Allows to chain apply/revert or push/pop chains of closures. Normally
 * <code>StateReverts</code> are executed in reverse order compared to their apply counterparts
 */
public interface StateRevert {
    /**
     * Encapsulates the logic to revert a specific action. The assumption is that
     * after {@link #revert()} all contextual state related to the prior
     * application of a state change is undone. In most cases it is a very good idea
     * to execute the revert() in a finally part of a try/finally clause.
     */
    void revert();
}
