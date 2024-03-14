package org.threadlys.utils.clone;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.Objects;

public class ClassIdObjectOutputStream extends ObjectOutputStream {

    protected final ClassIdState classIdState;

    public ClassIdObjectOutputStream(ClassIdState classIdState) throws IOException, SecurityException {
        super();
        this.classIdState = Objects.requireNonNull(classIdState);
    }

    public ClassIdObjectOutputStream(ClassIdState classIdState, OutputStream out) throws IOException {
        super(out);
        this.classIdState = Objects.requireNonNull(classIdState);
    }

    @Override
    protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
        var id = classIdState.resolveClassId(desc);
        writeShort(id);
    }
}
