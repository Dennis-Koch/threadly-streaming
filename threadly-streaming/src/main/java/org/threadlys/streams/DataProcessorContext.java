package org.threadlys.streams;

public interface DataProcessorContext {

    default Object extractDomainRef(Object entity) {
        return null;
    }
}
