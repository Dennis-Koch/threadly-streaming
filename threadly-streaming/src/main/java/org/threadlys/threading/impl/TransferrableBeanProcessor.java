package org.threadlys.threading.impl;

public interface TransferrableBeanProcessor {
    Object processCreation(String name, Object newBean);
}
