package org.threadlys.utils.clone;

import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClassIdState {
    protected final Map<ObjectStreamClass, Short> streamClassToIdMap = new IdentityHashMap<>();

    protected final Map<Short, ObjectStreamClass> idToStreamClassMap = new HashMap<>();

    protected final Lock readLock;

    protected final Lock writeLock;

    public ClassIdState() {
        var rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
    }

    public Boolean validateClassToDeserialize(ObjectStreamClass desc) {
        readLock.lock();
        try {
            // check if the given class was ever serialized by our own component
            // before deserializing it. this makes it robust against CWE-502
            return streamClassToIdMap.containsKey(desc);
        } finally {
            readLock.unlock();
        }
    }

    public ObjectStreamClass resolveClassDescriptor(short classId) {
        readLock.lock();
        try {
            return idToStreamClassMap.get(classId);
        } finally {
            readLock.unlock();
        }
    }

    public short resolveClassId(ObjectStreamClass desc) {
        Short classId;
        readLock.lock();
        try {
            classId = streamClassToIdMap.get(desc);
        } finally {
            readLock.unlock();
        }
        if (classId != null) {
            return classId;
        }
        writeLock.lock();
        try {
            if (streamClassToIdMap.size() == Short.MAX_VALUE) {
                throw new IllegalStateException("Too many classes indexed (" + streamClassToIdMap.size() + ")");
            }
            classId = streamClassToIdMap.computeIfAbsent(desc, d -> (short) (streamClassToIdMap.size() + 1));
            idToStreamClassMap.put(classId, desc);
        } finally {
            writeLock.unlock();
        }
        return classId;
    }
}
