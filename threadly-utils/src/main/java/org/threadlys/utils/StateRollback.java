package org.threadlys.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

//CHECKSTYLE: DeclarationOrderCheck OFF
@SuppressWarnings({ "PMD.AssignmentInOperand", "checkstyle:DeclarationOrderCheck" })
public abstract class StateRollback implements IStateRollback {
    private static final IStateRollback[] EMPTY_ARRAY = new IStateRollback[0];

    private static class StateRollbackChainImpl implements StateRollbackChain {
        private List<IStateRollback> rollbacks;

        private List<IStateRollback> firstRollbacks;

        private boolean firstAllowed = true;

        @Override
        public void first(IStateRollback rollback) {
            if (rollback == null || rollback == NONE) {
                return;
            }
            if (!firstAllowed) {
                throw new IllegalStateException("No allowed to call first() at this state");
            }
            if (firstRollbacks == null) {
                firstRollbacks = new ArrayList<>();
            }
            firstRollbacks.add(rollback);
        }

        @Override
        public void append(IStateRollback rollback) {
            firstAllowed = false;
            if (rollback == null || rollback == NONE) {
                return;
            }
            if (rollbacks == null) {
                rollbacks = new ArrayList<>();
            }
            rollbacks.add(rollback);
        }

        public List<IStateRollback> evaluateEffectiveRollbacks() {
            if (firstRollbacks == null) {
                return rollbacks;
            }
            if (rollbacks == null) {
                return firstRollbacks;
            }
            rollbacks.addAll(0, firstRollbacks);
            return rollbacks;
        }
    }

    private static final class GroupingStateRollback extends StateRollback {
        public GroupingStateRollback(IStateRollback[] rollbacks) {
            super(rollbacks);
        }

        @Override
        protected void doRollback() {
            // intended blank
        }
    }

    private static final IStateRollback NONE = new StateRollback(EMPTY_ARRAY) {
        @Override
        protected void doRollback() {
            // intended blank
        }

        @Override
        public String toString() {
            return "EmptyStateRollback";
        }
    };

    public static @NonNull IStateRollback empty() {
        return NONE;
    }

    /**
     * Collects in-progress rollback chains and reverts them on any exceptional case. This is very helpful to make a proper rollback if in-the-middle exceptions occur while creating multiple rollbacks
     * 
     * @param chainBuilder The chain
     * @return
     */
    public static @NonNull IStateRollback chain(@Nullable Consumer<StateRollbackChain> chainBuilder) {
        if (chainBuilder == null) {
            return NONE;
        }
        var success = false;
        var chain = new StateRollbackChainImpl();
        try {
            chainBuilder.accept(chain);
            success = true;
            return StateRollback.all(chain.evaluateEffectiveRollbacks());
        } finally {
            if (!success) {
                StateRollback.all(chain.rollbacks).rollback();
            }
        }
    }

    /**
     * Allows to append a new rollback to an existing array and return it as a single merged rollback handle. Note that their nested execution order is reversed: So LIFO or stack pattern oriented
     * 
     * @param rollbackToPrepend new rollback handle to prepend. May be null
     * @param rollbacks         rollback handles to prepend to. May be null or empty
     * @return A non-null rollback handle presenting the merged and ordered given sum of all rollback handles
     * 
     * @see #all(IStateRollback[])
     */
    public static @NonNull IStateRollback prepend(@Nullable IStateRollback rollbackToPrepend, @Nullable IStateRollback... rollbacks) {
        rollbacks = collapseRollbacks(mergeRollbacks(rollbackToPrepend, rollbacks));
        if (rollbacks.length == 0) {
            return NONE;
        }
        if (rollbacks.length == 1) {
            return rollbacks[0];
        }
        return new GroupingStateRollback(rollbacks);
    }

    /**
     * Allows to append a new rollback to an existing list and return it as a single merged rollback handle. Note that their nested execution order is reversed: So LIFO or stack pattern oriented -
     * hence the name
     * 
     * @param rollbackToPrepend new rollback handle to prepend. May be null
     * @param rollbacks         rollback handles to prepend to. May be null or empty
     * @return A non-null rollback handle presenting the merged and ordered given sum of all rollback handles
     * 
     * @see #all(IStateRollback[])
     */
    public static @NonNull IStateRollback prepend(@Nullable IStateRollback rollbackToPrepend, @Nullable List<IStateRollback> rollbacks) {
        if (rollbacks == null || rollbacks.isEmpty()) {
            return rollbackToPrepend != null ? rollbackToPrepend : NONE;
        }
        var rollbacksArray = collapseRollbacks(mergeRollbacks(rollbackToPrepend, rollbacks.toArray(IStateRollback[]::new)));
        if (rollbacksArray.length == 0) {
            return NONE;
        }
        if (rollbacksArray.length == 1) {
            return rollbacksArray[0];
        }
        return new GroupingStateRollback(rollbacksArray);
    }

    /**
     * Wraps multiple state rollbacks into a single rollback. Note that due to the stack-minded push/pop pattern the rollback execution is in inverse order compared to the given array.<br>
     * <br>
     * This method is useful if you have code that creates multiple state rollbacks independent from each other - this means you have no ability to pass the existing rollbacks while creating new
     * rollbacks. In such a scenario you can "unify" those rollbacks with this invocation into one handle in order to continue with less rollback handles to deal with manually.
     *
     * @param rollbacks The rollbacks to group into a single handle and to apply in reverse order when calling {@link #rollback()} on the returned handle. May be null or empty
     * @return A non-null rollback handle presenting the merged and ordered given sum of all rollback handles
     */
    public static @NonNull IStateRollback all(@Nullable IStateRollback... rollbacks) {
        rollbacks = collapseRollbacks(rollbacks);
        if (rollbacks.length == 0) {
            return NONE;
        }
        if (rollbacks.length == 1) {
            return rollbacks[0];
        }
        return new GroupingStateRollback(rollbacks);
    }

    /**
     * @see #all(IStateRollback...)
     * @param rollbacks The rollbacks to group into a single handle and to apply in reverse order when calling {@link #rollback()} on the returned handle. May be null or empty
     * @return A non-null rollback handle presenting the merged and ordered given sum of all rollback handles
     */
    public static @NonNull IStateRollback all(@Nullable List<IStateRollback> rollbacks) {
        if (rollbacks == null || rollbacks.size() == 0) {
            return NONE;
        }
        return all(rollbacks.toArray(IStateRollback[]::new));
    }

    protected static IStateRollback[] mergeRollbacks(IStateRollback newRollback, IStateRollback[] rollbacks) {
        if (newRollback == null) {
            return rollbacks;
        }
        if (rollbacks == null) {
            return new IStateRollback[] { newRollback };
        }
        var mergedRollbacks = new IStateRollback[rollbacks.length + 1];
        System.arraycopy(rollbacks, 0, mergedRollbacks, 0, rollbacks.length);
        mergedRollbacks[rollbacks.length] = newRollback;
        return mergedRollbacks;
    }

    protected static IStateRollback[] collapseRollbacks(IStateRollback[] rollbacks) {
        if (rollbacks == null || rollbacks.length == 0) {
            return EMPTY_ARRAY;
        }
        int collapsedRollbackCount = 0;
        boolean treeFlattening = false;
        for (int b = 0, size = rollbacks.length; b < size; b++) {
            var rollback = rollbacks[b];
            if (rollback == NONE || rollback == null) {
                continue;
            }
            if (rollback instanceof GroupingStateRollback) {
                collapsedRollbackCount += ((GroupingStateRollback) rollback).rollbacks.length;
                treeFlattening = true;
            } else {
                collapsedRollbackCount++;
            }
        }
        if (collapsedRollbackCount == 0) {
            return EMPTY_ARRAY;
        }
        if (!treeFlattening && collapsedRollbackCount == rollbacks.length) {
            // nothing to do
            return rollbacks;
        }
        var collapsedRollbacks = new IStateRollback[collapsedRollbackCount];
        var collapsedRollbackIndex = 0;
        for (int b = 0, size = rollbacks.length; b < size; b++) {
            var rollback = rollbacks[b];
            if (rollback == NONE || rollback == null) {
                continue;
            }
            if (rollback instanceof GroupingStateRollback) {
                for (IStateRollback nestedRollback : ((GroupingStateRollback) rollback).rollbacks) {
                    collapsedRollbacks[collapsedRollbackIndex++] = nestedRollback;
                }
            } else {
                collapsedRollbacks[collapsedRollbackIndex++] = rollback;
            }
        }
        return collapsedRollbacks;
    }

    final IStateRollback[] rollbacks;

    protected StateRollback(IStateRollback... rollbacks) {
        if (rollbacks == null || rollbacks.length == 0) {
            this.rollbacks = EMPTY_ARRAY;
        } else {
            this.rollbacks = rollbacks;
        }
    }

    /**
     * Needs to be implemented by a subclass and is invoked from {@link #rollback()}
     */
    protected abstract void doRollback();

    @Override
    public final void rollback() {
        try {
            doRollback();
        } finally {
            for (int a = rollbacks.length; a-- > 0;) {
                rollbacks[a].rollback();
            }
        }
    }
}
