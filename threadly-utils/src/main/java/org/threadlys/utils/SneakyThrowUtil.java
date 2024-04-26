package org.threadlys.utils;

public interface SneakyThrowUtil {
    <T extends RuntimeException, E extends Throwable> T sneakyThrow(Throwable e) throws E;
    
    Throwable mergeStackTraceWithCause(Throwable e);
}
