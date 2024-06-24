package org.threadlys.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultStateRevertTest {

    StateRevert revert1 = () -> {
    };

    StateRevert revert2 = () -> {
    };

    StateRevert revert3 = () -> {
    };

    @Test
    void rawNullCases() {
        assertThat(DefaultStateRevert.all((StateRevert) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all(DefaultStateRevert.empty())).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all((StateRevert[]) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all(null, null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all(DefaultStateRevert.empty(), null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all((List<StateRevert>) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all(new StateRevert[0])).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.all(List.of())).isSameAs(DefaultStateRevert.empty());

        assertThat(DefaultStateRevert.prepend((StateRevert) null, (StateRevert[]) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), (StateRevert[]) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend((StateRevert) null, new StateRevert[] { null })).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), new StateRevert[] { null })).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend((StateRevert) null, (StateRevert[]) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), (StateRevert[]) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend((StateRevert) null, new StateRevert[] { null })).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), new StateRevert[] { null })).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend((StateRevert) null, (List<StateRevert>) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), (List<StateRevert>) null)).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), List.of())).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), Collections.singletonList(null))).isSameAs(DefaultStateRevert.empty());
        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), new StateRevert[0])).isSameAs(DefaultStateRevert.empty());
    }

    @Test
    void simpleNullCases() {
        assertThat(((DefaultStateRevert) DefaultStateRevert.all(revert1, null, revert2)).reverts).hasSize(2);
        assertThat(DefaultStateRevert.all(null, null, revert1, null)).isSameAs(revert1);

        assertThat(((DefaultStateRevert) DefaultStateRevert.prepend(revert1, null, revert2)).reverts).hasSize(2);
        assertThat(DefaultStateRevert.prepend(null, null, revert1, null)).isSameAs(revert1);
    }

    @Test
    void flattenTree() {
        assertThat(((DefaultStateRevert) DefaultStateRevert.all(revert1, DefaultStateRevert.all(revert2, null, revert3), DefaultStateRevert.empty())).reverts) //
                .containsExactly(revert1, revert2, revert3);

        assertThat(((DefaultStateRevert) DefaultStateRevert.prepend(revert1, DefaultStateRevert.prepend(revert2, null, revert3), DefaultStateRevert.empty())).reverts) //
                .containsExactly(revert3, revert2, revert1);

        assertThat(((DefaultStateRevert) DefaultStateRevert.prepend(revert1, List.of(DefaultStateRevert.prepend(revert2, null, revert3), DefaultStateRevert.empty()))).reverts) //
                .containsExactly(revert3, revert2, revert1);

        assertThat(DefaultStateRevert.prepend(DefaultStateRevert.empty(), List.of(DefaultStateRevert.prepend(revert2, null, DefaultStateRevert.empty())))) //
                .isSameAs(revert2);
    }

    @Test
    void chainRevertOnException() {
        var callOrder = new ArrayList<Integer>();

        assertThrows(IllegalStateException.class, () -> DefaultStateRevert.chain(chain -> {
            chain.append(() -> callOrder.add(1));
            chain.append(() -> callOrder.add(2));
            throw new IllegalStateException();
        }).revert());

        assertThat(callOrder).containsExactly(2, 1);
    }

    @Test
    void chainRevertRegularly() {
        var callOrder = new ArrayList<Integer>();

        DefaultStateRevert.chain(chain -> {
            chain.append(() -> callOrder.add(1));
            chain.append(() -> callOrder.add(2));
            chain.append(() -> callOrder.add(3));
        }).revert();

        assertThat(callOrder).containsExactly(3, 2, 1);
    }

    @Test
    void chainNullBuilder() {
        assertThat(DefaultStateRevert.chain(null)).isEqualTo(DefaultStateRevert.empty());
    }

    @Test
    void chainEmptyBuilder() {
        assertThat(DefaultStateRevert.chain(chain -> {
        })).isEqualTo(DefaultStateRevert.empty());
    }

    @Test
    void chainWithFirstOnly() {
        var callOrder = new ArrayList<Integer>();

        StateRevert revert1 = () -> callOrder.add(1);
        StateRevert revert2 = () -> callOrder.add(2);
        DefaultStateRevert.chain(chain -> {
            chain.first(revert1);
            chain.first(revert2);
        }).revert();

        assertThat(callOrder).containsExactly(2, 1);
    }

    @Test
    void chainWithFirst() {
        var callOrder = new ArrayList<Integer>();

        StateRevert revert1 = () -> callOrder.add(1);
        StateRevert revert2 = () -> callOrder.add(2);
        DefaultStateRevert.chain(chain -> {
            chain.first(revert1);
            chain.first(revert2);
            chain.first(null);
            chain.first(DefaultStateRevert.empty());
            chain.append(() -> callOrder.add(3));
            chain.append(null);
            chain.append(DefaultStateRevert.empty());
            chain.append(() -> callOrder.add(4));
        }).revert();

        assertThat(callOrder).containsExactly(4, 3, 2, 1);
    }

    @Test
    void chainWithFirstTooLate() {
        var callOrder = new ArrayList<Integer>();

        StateRevert revert1 = () -> callOrder.add(1);

        assertThrows(IllegalStateException.class, () -> DefaultStateRevert.chain(chain -> {
            chain.append(() -> callOrder.add(3));
            chain.append(() -> callOrder.add(4));
            chain.first(revert1);
        }));

        assertThat(callOrder).containsExactly(4, 3);
    }

    @Test
    void empty() {
        DefaultStateRevert.empty().revert();

        assertThat(DefaultStateRevert.empty()).hasToString("EmptyStateRevert");
    }

    @Test
    void customDefaultStateRevert() {
        var callOrder = new ArrayList<Integer>();

        new DefaultStateRevert((StateRevert[]) null) {
            protected void doRevert() {
                callOrder.add(1);
            }
        }.revert();

        assertThat(callOrder).containsExactly(1);

        callOrder.clear();

        new DefaultStateRevert(DefaultStateRevert.empty()) {
            protected void doRevert() {
                callOrder.add(1);
            }
        }.revert();

        assertThat(callOrder).containsExactly(1);
    }
}
