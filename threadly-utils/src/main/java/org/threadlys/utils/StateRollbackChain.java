package org.threadlys.utils;

import org.springframework.lang.Nullable;

/**
 * Allows to collect rollbacks under construction.
 * 
 * @see {@link StateRollback#chain(java.util.function.Consumer)}
 */
public interface StateRollbackChain {
    /**
     * Appends a single rollback handle to chain if - and only if - the whole chain was created successfully afterwards. This is to support cases where a rollback handle is already created (and
     * assigned on a stack or heap variable). In such a case this rollback would be called twice if we do not distinguish it from the nested rollback handles (once due to the in-progress failed chain
     * algorithm, and a second time due to the outer scope managed "normal" rollback sequence). As a result we need to add a support in the chance to avoid the first rollback during the in-progress
     * failing chain phase. You may call this method at most once per chain creation.<br>
     * <br>
     * Usage example:<br>
     * <br>
     * <code>
     * var rollback = myFunnyCodeStateProducesRollback();<br>
     * rollback = StateRollback.chain(chain -> {<br>
     * &nbsp;&nbsp;chain.first(rollback);<br>
     * &nbsp;&nbsp;chain.append(foo());<br>
     * &nbsp;&nbsp;chain.append(bar());<br>
     * });<br>
     * </code>
     * 
     * @param rollback
     */
    void first(@Nullable IStateRollback rollback);

    /**
     * Appends a single rollback handle to chain. May be null. The chain logic ensures that also on partial creations of the chain - if an unexpected in-progress failure happens - the partial chain
     * still gets rolled back properly in reverse order<br>
     * <br>
     * Usage example:<br>
     * <br>
     * <code>
     * var rollback = StateRollback.chain(chain -> {<br>
     * &nbsp;&nbsp;chain.append(foo());<br>
     * &nbsp;&nbsp;chain.append(bar());<br>
     * });<br>
     * </code>
     * 
     * @param rollback The rollback handle to chain
     */
    void append(@Nullable IStateRollback rollback);
}
