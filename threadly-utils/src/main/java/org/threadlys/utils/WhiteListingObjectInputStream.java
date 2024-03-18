package org.threadlys.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

public abstract class WhiteListingObjectInputStream extends ObjectInputStream {

    protected WhiteListingObjectInputStream() throws IOException, SecurityException {
        super();
    }

    protected WhiteListingObjectInputStream(InputStream in) throws IOException {
        super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (!Boolean.TRUE.equals(validateClassToDeserialize(desc))) {
            throw new InvalidClassException("Unexpected serialized class to deserialize", desc.getName());
        }
        return super.resolveClass(desc);
    }

    protected abstract Boolean validateClassToDeserialize(ObjectStreamClass desc);
}
