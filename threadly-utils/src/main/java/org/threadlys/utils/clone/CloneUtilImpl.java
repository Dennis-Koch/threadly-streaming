package org.threadlys.utils.clone;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.WeakHashMap;

import org.threadlys.utils.CloneUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.threadlys.utils.FastByteArrayOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Setter;
import lombok.SneakyThrows;

//CHECKSTYLE: IllegalThrowsCheck OFF
@Component
public class CloneUtilImpl implements CloneUtil {
    protected final ClassIdState classIdState = new ClassIdState();

    protected final Map<Class<?>, Boolean> fallbackHandlingMap = new WeakHashMap<>();

    @Value("${threadlys.core.initial-clone-buffer:512}")
    protected int initialBufferSize;

    @Override
    public byte[] serialize(Object obj) {
        // we need a deserialization proxy so that we dont lose metadata for
        // deserialization
        var deserializationProxy = (DeserializationProxy) deepCloneInternal(obj, true);

        // the 2nd deserialization proxy is only required in order to get to
        // its raw byte array content (that entails original byte array
        // content meta data)
        return ((DeserializationProxy) deepCloneInternal(deserializationProxy, true)).getSerializedValue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(byte[] serializedValue) {
        var deserializationProxy = deserialize(new ByteArrayInputStream(serializedValue));
        return (T) deepCloneInternal(deserializationProxy, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deepClone(T obj) {
        return (T) deepCloneInternal(obj, false);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    protected <T> T deserialize(InputStream is) {
        try (var ois = new ClassIdObjectInputStream(classIdState, is)) {
            return (T) ois.readObject();
        }
    }

    @SneakyThrows
    protected Object deepCloneInternal(Object obj, boolean serializeOnly) {
        if (obj == null) {
            return null;
        }
        boolean doFallbackHandling;
        synchronized (fallbackHandlingMap) {
            if (!serializeOnly && obj instanceof DeserializationProxy) {
                doFallbackHandling = ((DeserializationProxy) obj).isFallbackSerialization();
            } else {
                doFallbackHandling = Boolean.TRUE.equals(fallbackHandlingMap.get(obj.getClass()));
            }
        }
        if (doFallbackHandling) {
            return deepCloneFallback(obj, serializeOnly, null);
        }
        // fast implementation of a true deep clone of an arbitrary
        // (serializable) Java object tree.
        // the trick here is that we completely omit the expensive class
        // descriptor in our output stream as it is completely rendundant
        // due to our in-memory serialization-immediate-deserialization
        // algorithm. as a result we safe temporary memory footprint and CPU
        // cycles due to much smaller byte-array buffers
        try {
            if (!serializeOnly && obj instanceof DeserializationProxy) {
                return deserialize(new ByteArrayInputStream(((DeserializationProxy) obj).getSerializedValue()));
            }
            try (var bos = new FastByteArrayOutputStream(initialBufferSize); var oos = new ClassIdObjectOutputStream(classIdState, bos)) {
                oos.writeObject(obj);
                oos.flush();

                if (serializeOnly) {
                    return new DeserializationProxy(bos.toByteArray(), null, false);
                }
                return deserialize(bos.createInputStream());
            }
        } catch (IOException e) {
            Object clonedObj = deepCloneFallback(obj, serializeOnly, e);
            synchronized (fallbackHandlingMap) {
                // mark fallback knowledge for next cloning operation
                fallbackHandlingMap.put(obj.getClass(), Boolean.TRUE);
            }
            return clonedObj;
        }
    }

    @SneakyThrows
    protected Object deepCloneFallback(Object obj, boolean serializeOnly, Throwable originalException) {
        // fallback implementation, but this requires JSON compatibility:
        try {
            if (!serializeOnly && obj instanceof DeserializationProxy) {
                var ch = (DeserializationProxy) obj;
                return new ObjectMapper().readValue(ch.getSerializedValue(), ch.getOriginalType());
            }
            var bos = new FastByteArrayOutputStream(initialBufferSize);
            new ObjectMapper().writeValue(bos, obj);
            if (serializeOnly) {
                return new DeserializationProxy(bos.toByteArray(), obj.getClass(), true);
            }
            return new ObjectMapper().readValue(bos.createInputStream(), obj.getClass());
        } catch (IOException ex) {
            if (originalException != null) {
                throw originalException;
            } else {
                throw ex;
            }
        }
    }
}
