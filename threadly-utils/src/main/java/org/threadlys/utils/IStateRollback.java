package org.threadlys.utils;

/**
 * Allows to chain apply/rollback or push/pop chains of closures. Normally
 * rollbacks are executed in reverse order compared to their apply counterparts
 */
public interface IStateRollback {
    /**
     * Encapsulates the logic to rollback a specific action. The assumption is that
     * after {@link #rollback()} all contextual state related to the prior
     * application of a state change is undone. In most cases it is a very good idea
     * to execute the rollback() in a finally part of a try/finally clause.
     */
    void rollback();
}
