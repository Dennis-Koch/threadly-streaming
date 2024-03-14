package org.threadlys.utils.clone;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.util.Objects;

import org.threadlys.utils.WhiteListingObjectInputStream;

public class ClassIdObjectInputStream extends WhiteListingObjectInputStream {

    protected final ClassIdState classIdState;

    public ClassIdObjectInputStream(ClassIdState classIdState, InputStream in) throws IOException {
        super(in);
        this.classIdState = Objects.requireNonNull(classIdState);
    }

    @Override
    protected Boolean validateClassToDeserialize(ObjectStreamClass desc) {
        return classIdState.validateClassToDeserialize(desc);
    }

    @Override
    protected ObjectStreamClass readClassDescriptor() throws IOException {
        var id = readShort();
        return classIdState.resolveClassDescriptor(id);
    }
}
