package org.threadlys.digest;

public interface ThreadLocalMessageDigest {
    byte[] digestSHA256(byte[] data);

    byte[] digestSHA512(byte[] data);
}
