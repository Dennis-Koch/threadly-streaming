package org.threadlys.utils;

import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Allows to deep-clone an object. The to-be-cloned object must either be
 * {@link Serializable} or support plain POJO-to-JSON serialization and
 * therefore provides mutable fields accessed with public accessors and must
 * provide at least the default (= parameter-less) constructor.<br>
 * <br>
 * The underlying implementation tries first to serialize the given object via
 * the standard java object serialization. If that fails it tries to fallback to
 * the JSON serialization.
 *
 * @author Dennis Koch (EXXETA AG)
 *
 */
public interface CloneUtil {

    /**
     * Create a serialized representation of the given object. Note that this
     * result value may not necessarily represent the exact-and-only given
     * object, but may contain also additional metadata that is required for
     * deserializing the object transparently (e.g. potential metadata related
     * to the POJO-to-JSON fallback serialization).<br>
     * <br>
     * Note that due to these transparent features the following restrictions
     * apply to the usage of the returned byte array:
     * <ul>
     * <li>the byte array must only be deserialized with the given counterpart
     * method {@link #deserialize(byte[])}</li>
     * <li>the same <b>CloneUtil</b> instance must be used for deserialization
     * that was used for serialization</li>
     * <li>as a consequence it makes no sense to transfer the byte array out of
     * the current JVM - e.g. no value in persisting it to a file or sending it
     * over the network to another process</li>
     * <ul>
     *
     * @param obj
     *            The object to serialize. Must be either serializable or
     *            POJO/JSON compatible
     * @return a serialized representation of the given object. the protocol of
     *         the byte array may be proprietary and only deserializable by
     *         {@link #deserialize(byte[])}
     */
    byte[] serialize(Object obj);

    /**
     * Reconstructs a POJO representation of the given serialized value. The
     * given byte array must have been created prior with
     * {@link #serialize(Object)} <b>from the same CloneUtil instance</b>
     *
     * @param <T>
     *            Expected deserialized Java object type
     * @param serializedValue
     *            Serialized representation of the Java object
     * @return The deserialized Java object
     */
    <T> T deserialize(byte[] serializedValue);

    /**
     * Recursively creates a cloned instance of the given Java object. The
     * cloning mechanism is an implementation detail but normally a highly
     * optimized usage of {@link ObjectOutputStream} is used for POJOs that
     * implement {@link Serializable}. Fallback methods may be provided
     * transparently to serialize e.g. a POJO via Java Beans pattern to JSON and
     * read it back in to achieve a close-as-possible transparent effect.
     *
     * @param <T>
     *            The to-be-cloned Java object type
     * @param obj
     *            The to-be-cloned Java object
     * @return A cloned instance of the given Java object. Transitive object
     *         cycles may not be guaranteed if fallback serialization methods
     *         have been applied (e.g. due to missing {@link Serializable}
     *         compatibility)
     */
    <T> T deepClone(T obj);
}
