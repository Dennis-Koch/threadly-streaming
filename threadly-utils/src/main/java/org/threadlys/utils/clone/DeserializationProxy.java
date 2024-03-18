package org.threadlys.utils.clone;

import java.io.Serializable;

import lombok.Value;

@Value
public class DeserializationProxy implements Serializable {
    private static final long serialVersionUID = 4470616181100596068L;

    private final byte[] serializedValue;

    private final Class<?> originalType;

    private final boolean fallbackSerialization;
}
