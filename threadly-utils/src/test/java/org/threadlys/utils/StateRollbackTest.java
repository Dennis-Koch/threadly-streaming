package org.threadlys.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class StateRollbackTest {

    IStateRollback stateRollback1 = () -> {
    };

    IStateRollback stateRollback2 = () -> {
    };

    IStateRollback stateRollback3 = () -> {
    };

    @Test
    void rawNullCases() {
        assertThat(StateRollback.all((IStateRollback) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all(StateRollback.empty())).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all((IStateRollback[]) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all(null, null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all(StateRollback.empty(), null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all((List<IStateRollback>) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all(new IStateRollback[0])).isSameAs(StateRollback.empty());
        assertThat(StateRollback.all(List.of())).isSameAs(StateRollback.empty());

        assertThat(StateRollback.prepend((IStateRollback) null, (IStateRollback[]) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), (IStateRollback[]) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend((IStateRollback) null, new IStateRollback[] { null })).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), new IStateRollback[] { null })).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend((IStateRollback) null, (IStateRollback[]) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), (IStateRollback[]) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend((IStateRollback) null, new IStateRollback[] { null })).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), new IStateRollback[] { null })).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend((IStateRollback) null, (List<IStateRollback>) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), (List<IStateRollback>) null)).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), List.of())).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), Collections.singletonList(null))).isSameAs(StateRollback.empty());
        assertThat(StateRollback.prepend(StateRollback.empty(), new IStateRollback[0])).isSameAs(StateRollback.empty());
    }

    @Test
    void simpleNullCases() {
        assertThat(((StateRollback) StateRollback.all(stateRollback1, null, stateRollback2)).rollbacks).hasSize(2);
        assertThat(StateRollback.all(null, null, stateRollback1, null)).isSameAs(stateRollback1);

        assertThat(((StateRollback) StateRollback.prepend(stateRollback1, null, stateRollback2)).rollbacks).hasSize(2);
        assertThat(StateRollback.prepend(null, null, stateRollback1, null)).isSameAs(stateRollback1);
    }

    @Test
    void flattenTree() {
        assertThat(((StateRollback) StateRollback.all(stateRollback1, StateRollback.all(stateRollback2, null, stateRollback3), StateRollback.empty())).rollbacks) //
                .containsExactly(stateRollback1, stateRollback2, stateRollback3);

        assertThat(((StateRollback) StateRollback.prepend(stateRollback1, StateRollback.prepend(stateRollback2, null, stateRollback3), StateRollback.empty())).rollbacks) //
                .containsExactly(stateRollback3, stateRollback2, stateRollback1);

        assertThat(((StateRollback) StateRollback.prepend(stateRollback1, List.of(StateRollback.prepend(stateRollback2, null, stateRollback3), StateRollback.empty()))).rollbacks) //
                .containsExactly(stateRollback3, stateRollback2, stateRollback1);

        assertThat(StateRollback.prepend(StateRollback.empty(), List.of(StateRollback.prepend(stateRollback2, null, StateRollback.empty())))) //
                .isSameAs(stateRollback2);
    }

    @Test
    void chainRollbackOnException() {
        var callOrder = new ArrayList<Integer>();

        assertThrows(IllegalStateException.class, () -> StateRollback.chain(chain -> {
            chain.append(() -> callOrder.add(1));
            chain.append(() -> callOrder.add(2));
            throw new IllegalStateException();
        }).rollback());

        assertThat(callOrder).containsExactly(2, 1);
    }

    @Test
    void chainRollbackRegularly() {
        var callOrder = new ArrayList<Integer>();

        StateRollback.chain(chain -> {
            chain.append(() -> callOrder.add(1));
            chain.append(() -> callOrder.add(2));
            chain.append(() -> callOrder.add(3));
        }).rollback();

        assertThat(callOrder).containsExactly(3, 2, 1);
    }

    @Test
    void chainNullBuilder() {
        assertThat(StateRollback.chain(null)).isEqualTo(StateRollback.empty());
    }

    @Test
    void chainEmptyBuilder() {
        assertThat(StateRollback.chain(chain -> {
        })).isEqualTo(StateRollback.empty());
    }

    @Test
    void chainWithFirstOnly() {
        var callOrder = new ArrayList<Integer>();

        IStateRollback rollback1 = () -> callOrder.add(1);
        IStateRollback rollback2 = () -> callOrder.add(2);
        StateRollback.chain(chain -> {
            chain.first(rollback1);
            chain.first(rollback2);
        }).rollback();

        assertThat(callOrder).containsExactly(2, 1);
    }

    @Test
    void chainWithFirst() {
        var callOrder = new ArrayList<Integer>();

        IStateRollback rollback1 = () -> callOrder.add(1);
        IStateRollback rollback2 = () -> callOrder.add(2);
        StateRollback.chain(chain -> {
            chain.first(rollback1);
            chain.first(rollback2);
            chain.first(null);
            chain.first(StateRollback.empty());
            chain.append(() -> callOrder.add(3));
            chain.append(null);
            chain.append(StateRollback.empty());
            chain.append(() -> callOrder.add(4));
        }).rollback();

        assertThat(callOrder).containsExactly(4, 3, 2, 1);
    }

    @Test
    void chainWithFirstTooLate() {
        var callOrder = new ArrayList<Integer>();

        IStateRollback rollback1 = () -> callOrder.add(1);

        assertThrows(IllegalStateException.class, () -> StateRollback.chain(chain -> {
            chain.append(() -> callOrder.add(3));
            chain.append(() -> callOrder.add(4));
            chain.first(rollback1);
        }));

        assertThat(callOrder).containsExactly(4, 3);
    }

    @Test
    void empty() {
        StateRollback.empty().rollback();

        assertThat(StateRollback.empty()).hasToString("EmptyStateRollback");
    }

    @Test
    void customStateRollback() {
        var callOrder = new ArrayList<Integer>();

        new StateRollback((IStateRollback[]) null) {
            protected void doRollback() {
                callOrder.add(1);
            }
        }.rollback();

        assertThat(callOrder).containsExactly(1);

        callOrder.clear();

        new StateRollback(StateRollback.empty()) {
            protected void doRollback() {
                callOrder.add(1);
            }
        }.rollback();

        assertThat(callOrder).containsExactly(1);
    }
}
