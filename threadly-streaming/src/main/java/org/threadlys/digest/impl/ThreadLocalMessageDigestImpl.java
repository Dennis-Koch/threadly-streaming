package org.threadlys.digest.impl;

import java.security.MessageDigest;

import org.springframework.stereotype.Component;

import org.threadlys.digest.ThreadLocalMessageDigest;
import org.threadlys.threading.ThreadLocalScope;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;

@ThreadLocalScope
@Component
public class ThreadLocalMessageDigestImpl implements ThreadLocalMessageDigest {

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    private final MessageDigest sha256 = createSHA256();

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    private final MessageDigest sha512 = createSHA512();

    @SneakyThrows
    protected MessageDigest createSHA256() {
        return MessageDigest.getInstance("SHA-256");
    }

    @SneakyThrows
    protected MessageDigest createSHA512() {
        return MessageDigest.getInstance("SHA-512");
    }

    @Override
    public byte[] digestSHA256(byte[] data) {
        return getSha256().digest(data);
    }

    @Override
    public byte[] digestSHA512(byte[] data) {
        return getSha512().digest(data);
    }
}
