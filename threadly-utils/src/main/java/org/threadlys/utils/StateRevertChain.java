package org.threadlys.utils;

import org.springframework.lang.Nullable;

/**
 * Allows to collect {@link StateRevert}s under construction.
 * 
 * @see {@link DefaultStateRevert#chain(java.util.function.Consumer)}
 */
public interface StateRevertChain {
    /**
     * Appends a single revert handle to chain if - and only if - the whole chain was created successfully afterwards. This is to support cases where a revert handle is already created (and
     * assigned on a stack or heap variable). In such a case this revert would be called twice if we do not distinguish it from the nested revert handles (once due to the in-progress failed chain
     * algorithm, and a second time due to the outer scope managed "normal" revert sequence). As a result we need to add a support in the chance to avoid the first revert during the in-progress
     * failing chain phase. You may call this method at most once per chain creation.<br>
     * <br>
     * Usage example:<br>
     * <br>
     * <code>
     * var revert = myFunnyCodeStateProducesRevert();<br>
     * var chainedRevert = DefaultStateRevert.chain(chain -> {<br>
     * &nbsp;&nbsp;chain.first(revert);<br>
     * &nbsp;&nbsp;chain.append(foo());<br>
     * &nbsp;&nbsp;chain.append(bar());<br>
     * });<br>
     * </code>
     * 
     * @param revert
     */
    void first(@Nullable StateRevert revert);

    /**
     * Appends a single revert handle to chain. May be null. The chain logic ensures that also on partial creations of the chain - if an unexpected in-progress failure happens - the partial chain
     * still gets rolled back properly in reverse order<br>
     * <br>
     * Usage example:<br>
     * <br>
     * <code>
     * var revert = DefaultStateRevert.chain(chain -> {<br>
     * &nbsp;&nbsp;chain.append(foo());<br>
     * &nbsp;&nbsp;chain.append(bar());<br>
     * });<br>
     * </code>
     * 
     * @param revert The revert handle to chain
     */
    void append(@Nullable StateRevert revert);
}
