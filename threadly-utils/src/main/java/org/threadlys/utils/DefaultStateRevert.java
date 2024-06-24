package org.threadlys.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

//CHECKSTYLE: DeclarationOrderCheck OFF
@SuppressWarnings({ "PMD.AssignmentInOperand", "checkstyle:DeclarationOrderCheck" })
public abstract class DefaultStateRevert implements StateRevert {
    private static final StateRevert[] EMPTY_ARRAY = new StateRevert[0];

    private static class StateRevertChainImpl implements StateRevertChain {
        private List<StateRevert> reverts;

        private List<StateRevert> firstReverts;

        private boolean firstAllowed = true;

        @Override
        public void first(StateRevert revert) {
            if (revert == null || revert == NONE) {
                return;
            }
            if (!firstAllowed) {
                throw new IllegalStateException("No allowed to call first() at this state");
            }
            if (firstReverts == null) {
                firstReverts = new ArrayList<>();
            }
            firstReverts.add(revert);
        }

        @Override
        public void append(StateRevert revert) {
            firstAllowed = false;
            if (revert == null || revert == NONE) {
                return;
            }
            if (reverts == null) {
                reverts = new ArrayList<>();
            }
            reverts.add(revert);
        }

        public List<StateRevert> evaluateEffectiveReverts() {
            if (firstReverts == null) {
                return reverts;
            }
            if (reverts == null) {
                return firstReverts;
            }
            reverts.addAll(0, firstReverts);
            return reverts;
        }
    }

    private static final class GroupingStateRevert extends DefaultStateRevert {
        public GroupingStateRevert(StateRevert[] reverts) {
            super(reverts);
        }

        @Override
        protected void doRevert() {
            // intended blank
        }
    }

    private static final StateRevert NONE = new DefaultStateRevert(EMPTY_ARRAY) {
        @Override
        protected void doRevert() {
            // intended blank
        }

        @Override
        public String toString() {
            return "EmptyStateRevert";
        }
    };

    public static @NonNull StateRevert empty() {
        return NONE;
    }

    /**
     * Collects in-progress revert chains and reverts them on any exceptional case. This is very helpful to make a proper revert if in-the-middle exceptions occur while creating multiple reverts
     * 
     * @param chainBuilder The chain
     * @return
     */
    public static @NonNull StateRevert chain(@Nullable Consumer<StateRevertChain> chainBuilder) {
        if (chainBuilder == null) {
            return NONE;
        }
        var success = false;
        var chain = new StateRevertChainImpl();
        try {
            chainBuilder.accept(chain);
            success = true;
            return DefaultStateRevert.all(chain.evaluateEffectiveReverts());
        } finally {
            if (!success) {
                DefaultStateRevert.all(chain.reverts).revert();
            }
        }
    }

    /**
     * Allows to append a new <code>Revert</code> to an existing array and return it as a single merged revert handle. Note that their nested execution order is reversed: So LIFO or stack pattern oriented
     * 
     * @param revertToPrepend new revert handle to prepend. May be null
     * @param reverts         revert handles to prepend to. May be null or empty
     * @return A non-null revert handle presenting the merged and ordered given sum of all revert handles
     * 
     * @see #all(StateRevert[])
     */
    public static @NonNull StateRevert prepend(@Nullable StateRevert revertToPrepend, @Nullable StateRevert... reverts) {
        reverts = collapseReverts(mergeReverts(revertToPrepend, reverts));
        if (reverts.length == 0) {
            return NONE;
        }
        if (reverts.length == 1) {
            return reverts[0];
        }
        return new GroupingStateRevert(reverts);
    }

    /**
     * Allows to append a new <code>Revert</code>> to an existing list and return it as a single merged revert handle. Note that their nested execution order is reversed: So LIFO or stack pattern oriented -
     * hence the name
     * 
     * @param revertToPrepend new revert handle to prepend. May be null
     * @param reverts         revert handles to prepend to. May be null or empty
     * @return A non-null revert handle presenting the merged and ordered given sum of all revert handles
     * 
     * @see #all(StateRevert[])
     */
    public static @NonNull StateRevert prepend(@Nullable StateRevert revertToPrepend, @Nullable List<StateRevert> reverts) {
        if (reverts == null || reverts.isEmpty()) {
            return revertToPrepend != null ? revertToPrepend : NONE;
        }
        var revertsArray = collapseReverts(mergeReverts(revertToPrepend, reverts.toArray(StateRevert[]::new)));
        if (revertsArray.length == 0) {
            return NONE;
        }
        if (revertsArray.length == 1) {
            return revertsArray[0];
        }
        return new GroupingStateRevert(revertsArray);
    }

    /**
     * Wraps multiple state reverts into a single revert. Note that due to the stack-minded push/pop pattern the revert execution is in inverse order compared to the given array.<br>
     * <br>
     * This method is useful if you have code that creates multiple state reverts independent from each other - this means you have no ability to pass the existing reverts while creating new
     * reverts. In such a scenario you can "unify" those reverts with this invocation into one handle in order to continue with less revert handles to deal with manually.
     *
     * @param reverts The reverts to group into a single handle and to apply in reverse order when calling {@link #revert()} on the returned handle. May be null or empty
     * @return A non-null revert handle presenting the merged and ordered given sum of all revert handles
     */
    public static @NonNull StateRevert all(@Nullable StateRevert... reverts) {
        reverts = collapseReverts(reverts);
        if (reverts.length == 0) {
            return NONE;
        }
        if (reverts.length == 1) {
            return reverts[0];
        }
        return new GroupingStateRevert(reverts);
    }

    /**
     * @see #all(StateRevert...)
     * @param reverts The reverts to group into a single handle and to apply in reverse order when calling {@link #revert()} on the returned handle. May be null or empty
     * @return A non-null revert handle presenting the merged and ordered given sum of all revert handles
     */
    public static @NonNull StateRevert all(@Nullable List<StateRevert> reverts) {
        if (reverts == null || reverts.size() == 0) {
            return NONE;
        }
        return all(reverts.toArray(StateRevert[]::new));
    }

    protected static StateRevert[] mergeReverts(StateRevert newRevert, StateRevert[] reverts) {
        if (newRevert == null) {
            return reverts;
        }
        if (reverts == null) {
            return new StateRevert[] { newRevert };
        }
        var mergedReverts = new StateRevert[reverts.length + 1];
        System.arraycopy(reverts, 0, mergedReverts, 0, reverts.length);
        mergedReverts[reverts.length] = newRevert;
        return mergedReverts;
    }

    protected static StateRevert[] collapseReverts(StateRevert[] reverts) {
        if (reverts == null || reverts.length == 0) {
            return EMPTY_ARRAY;
        }
        int collapsedRevertCount = 0;
        boolean treeFlattening = false;
        for (int b = 0, size = reverts.length; b < size; b++) {
            var revert = reverts[b];
            if (revert == NONE || revert == null) {
                continue;
            }
            if (revert instanceof GroupingStateRevert) {
                collapsedRevertCount += ((GroupingStateRevert) revert).reverts.length;
                treeFlattening = true;
            } else {
                collapsedRevertCount++;
            }
        }
        if (collapsedRevertCount == 0) {
            return EMPTY_ARRAY;
        }
        if (!treeFlattening && collapsedRevertCount == reverts.length) {
            // nothing to do
            return reverts;
        }
        var collapsedReverts = new StateRevert[collapsedRevertCount];
        var collapsedRevertIndex = 0;
        for (int b = 0, size = reverts.length; b < size; b++) {
            var revert = reverts[b];
            if (revert == NONE || revert == null) {
                continue;
            }
            if (revert instanceof GroupingStateRevert) {
                for (var nestedRevert : ((GroupingStateRevert) revert).reverts) {
                    collapsedReverts[collapsedRevertIndex++] = nestedRevert;
                }
            } else {
                collapsedReverts[collapsedRevertIndex++] = revert;
            }
        }
        return collapsedReverts;
    }

    final StateRevert[] reverts;

    protected DefaultStateRevert(StateRevert... reverts) {
        if (reverts == null || reverts.length == 0) {
            this.reverts = EMPTY_ARRAY;
        } else {
            this.reverts = reverts;
        }
    }

    /**
     * Needs to be implemented by a subclass and is invoked from {@link #revert()}
     */
    protected abstract void doRevert();

    @Override
    public final void revert() {
        try {
            doRevert();
        } finally {
            for (int a = reverts.length; a-- > 0;) {
                reverts[a].revert();
            }
        }
    }
}
