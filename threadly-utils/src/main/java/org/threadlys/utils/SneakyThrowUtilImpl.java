package org.threadlys.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Component;

@Component
public class SneakyThrowUtilImpl implements SneakyThrowUtil {
    @SuppressWarnings("unchecked")
    @Override
    public <T extends RuntimeException, E extends Throwable> T sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @Override
    public Throwable mergeStackTraceWithCause(Throwable e) {
        if (!(e instanceof ExecutionException) && !(e instanceof InvocationTargetException)) {
            return e;
        }

        StackTraceElement[] stackTrace = e.getStackTrace();
        // catch (ExecutionException e) {
        // var stes = e.getStackTrace();
        // throw e.getCause();
        // }
        //
        // catch (RuntimeException e) {
        // var stes = e.getStackTrace();
        // throw e.getCause();
        // }

        var cause = e.getCause();
        if (cause != null && (e.getMessage() == null //
                || Objects.equals(cause.getClass()
                        .getName(), e.getMessage()) //
                || Objects.equals(cause.getClass()
                        .getName() + ": " + cause.getMessage(), e.getMessage()))) {

            var causeTrace = cause.getStackTrace();

            // 0 java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
            // 1 java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)
            // 2 java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
            // 3 java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:499)
            // 4 java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:480)
            // 5 java.base/java.util.concurrent.ForkJoinTask.getThrowableException(ForkJoinTask.java:562)
            if (causeTrace.length > 5 && "java.util.concurrent.ForkJoinTask".equals(causeTrace[5].getClassName())) {
                cause = cause.getCause();
                causeTrace = cause.getStackTrace();
            }

            // 0 java.base/java.util.concurrent.ForkJoinTask$AdaptedInterruptibleCallable.exec(ForkJoinTask.java:1466)
            // 1 java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:373)
            // 2 java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1182)
            // 3 java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1655)
            // 4 java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1622)
            // 5 java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:165)
            if (causeTrace.length > 0 && "java.util.concurrent.ForkJoinTask$AdaptedInterruptibleCallable".equals(causeTrace[0].getClassName())) {
                cause = cause.getCause();
                causeTrace = cause.getStackTrace();
            }
            var executionTrace = e.getStackTrace();
            var mergedTrace = new StackTraceElement[causeTrace.length + executionTrace.length];
            System.arraycopy(causeTrace, 0, mergedTrace, 0, causeTrace.length);
            System.arraycopy(executionTrace, 0, mergedTrace, causeTrace.length, executionTrace.length);
            cause.setStackTrace(mergedTrace);
            return cause;
        }
        return e;
    }
}
