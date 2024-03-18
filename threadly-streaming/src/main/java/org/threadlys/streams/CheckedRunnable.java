package org.threadlys.streams;

/**
 * A {@link Runnable}-like interface which allows throwing checked exceptions.
 */
@FunctionalInterface
public interface CheckedRunnable {
    void run() throws Exception;
}
