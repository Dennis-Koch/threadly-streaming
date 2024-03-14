package org.threadlys.utils;

import java.util.Collection;
import java.util.Objects;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:JavadocMethod" })
public final class ListenersListAdapter {
    public static <L> void registerListener(L listener, Collection<L> listeners) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(listeners, "listeners must be valid");
        synchronized (listeners) {
            if (listeners.contains(listener)) {
                throw new IllegalStateException("Listener already registered: " + listener);
            }
            listeners.add(listener);
        }
    }

    public static <L> void unregisterListener(L listener, Collection<L> listeners) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(listeners, "listeners must be valid");
        synchronized (listeners) {
            boolean removed = listeners.remove(listener);
            if (!removed) {
                throw new IllegalStateException("Listener not registered: " + listener);
            }
        }
    }

    private ListenersListAdapter() {
        // intended blank
    }
}
