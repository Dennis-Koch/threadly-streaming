package org.threadlys.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Allows to retrieve the internal buffer directly - without cloning it. Very
 * useful in cases where an output stream is only used within a single method
 * and the underlying byte array is immediately used e.g. in order to contruct a
 * {@link ByteArrayInputStream}.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public class FastByteArrayOutputStream extends ByteArrayOutputStream {

    public FastByteArrayOutputStream() {
        super();
    }

    public FastByteArrayOutputStream(int size) {
        super(size);
    }

    public byte[] toByteArrayShared() {
        return buf;
    }

    public ByteArrayInputStream createInputStream() {
        return new ByteArrayInputStream(toByteArrayShared(), 0, size());
    }
}
