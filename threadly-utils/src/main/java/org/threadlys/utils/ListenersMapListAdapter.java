package org.threadlys.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "PMD.CompareObjectsWithEquals", "PMD.FormalParameterNamingConventions", "checkstyle:JavadocMethod" })
public final class ListenersMapListAdapter {
    public static <L, T> void registerListener(L listener, T key, Map<? super T, List<? super L>> keyToListenersMap) {
        registerListener(listener, key, keyToListenersMap, () -> new ArrayList<>());
    }

    public static <L, T> void registerListener(L listener, T key, Map<? super T, List<? super L>> keyToListenersMap, Supplier<List<? super L>> listFactory) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(keyToListenersMap, "keyToListenerMap must be valid");
        synchronized (keyToListenersMap) {
            var listeners = keyToListenersMap.computeIfAbsent(key, currentKey -> listFactory.get());
            listeners = new ArrayList<>(listeners); // clone for maximum thread safety
            if (listeners.contains(listener)) {
                throw new IllegalStateException("Listener already registered: " + listener + " with key: " + key);
            }
            listeners.add(listener);
            keyToListenersMap.put(key, listeners); // update value reference
        }
    }

    public static <L, T> void unregisterListener(L listener, T key, Map<? super T, List<? super L>> keyToListenersMap) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(keyToListenersMap, "listeners must be valid");
        synchronized (keyToListenersMap) {
            var listeners = keyToListenersMap.get(key);
            var removed = false;
            if (listeners != null) {
                listeners = new ArrayList<>(listeners); // clone for maximum thread safety
                removed = listeners.remove(listener);
            }
            if (!removed) {
                throw new IllegalStateException("Listener not registered: " + listener + " with key: " + key);
            }
            if (listeners.isEmpty()) {
                keyToListenersMap.remove(key);
            } else {
                keyToListenersMap.put(key, listeners); // update value reference
            }
        }
    }

    private ListenersMapListAdapter() {
        // intended blank
    }
}
