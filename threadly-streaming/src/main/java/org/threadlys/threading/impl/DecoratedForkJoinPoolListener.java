package org.threadlys.threading.impl;

public interface DecoratedForkJoinPoolListener {

    void threadPushed(DecoratedForkJoinPool forkJoinPool, Thread fjpThread);

    void threadPopped(DecoratedForkJoinPool forkJoinPool, Thread fjpThread);
}
