package org.threadlys.utils;

import java.util.Map;
import java.util.Objects;

//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "PMD.CompareObjectsWithEquals", "checkstyle:JavadocMethod" })
public final class ListenersMapAdapter {
    public static <L, T> void registerListener(L listener, T key, Map<? super T, ? super L> keyToListenerMap) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(keyToListenerMap, "keyToListenerMap must be valid");
        synchronized (keyToListenerMap) {
            if (keyToListenerMap.putIfAbsent(key, listener) != null) {
                throw new IllegalStateException("Listener already registered: " + listener + " with key: " + key);
            }
        }
    }

    public static <L, T> void unregisterListener(L listener, T key, Map<? super T, ? super L> keyToListenerMap) {
        Objects.requireNonNull(listener, "listener must be valid");
        Objects.requireNonNull(keyToListenerMap, "listeners must be valid");
        synchronized (keyToListenerMap) {
            if (!keyToListenerMap.remove(key, listener)) {
                throw new IllegalStateException("Listener registered '" + keyToListenerMap.get(key) + "' does not match expected '" + listener + " with key: " + key);
            }
        }
    }

    private ListenersMapAdapter() {
        // intended blank
    }
}
