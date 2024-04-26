package org.threadlys.utils.clone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.Data;

@ExtendWith(SpringExtension.class)
class CloneUtilTest {

    @Configuration
    @Import({ CloneUtilImpl.class })
    public static class Config {

    }

    @Autowired
    protected CloneUtilImpl fixture;

    protected Object obj1 = Integer.valueOf(1);

    protected JsonStruct json1;

    @Data
    public static class JsonStruct {
        int myField;
    }

    private byte[] serializedDeserProxy;

    @BeforeEach
    void beforeEach() {
        serializedDeserProxy = fixture.serialize(obj1);

        json1 = new JsonStruct();
        json1.setMyField(1);
    }

    @Test
    void testCloneNull() {
        assertThat((Integer) fixture.deepClone(null)).isNull();
    }

    @Test
    void testDeserialize() {
        assertThat((Integer) fixture.deserialize(serializedDeserProxy)).isEqualTo(obj1);
    }

    @Test
    void testDeserialize2() {
        assertThat((Integer) fixture.deserialize(serializedDeserProxy)).isEqualTo(obj1);
    }

    @Test
    void testDeepCloneInternalObjectFalse() {
        var cloneResult = fixture.deepClone(obj1);
        assertThat(cloneResult).isInstanceOf(Integer.class).isNotSameAs(obj1).isEqualTo(obj1);
    }

    @Test
    void testDeepCloneInternalObjectTrue() {
        var expected = new byte[] { -84, -19, 0, 5, 115, 114, 0, 1, 120, 114, 0, 2, 120, 112, 0, 0, 0, 1 };
        var cloneResult = fixture.deepCloneInternal(obj1, true);
        assertThat(cloneResult).isInstanceOf(DeserializationProxy.class);
        assertThat(((DeserializationProxy) cloneResult).getOriginalType()).isNull();
        assertThat(((DeserializationProxy) cloneResult).getSerializedValue()).containsExactly(expected);
    }

    @Test
    void testDeepCloneInternalProxyFalse() {
        var deserializationProxy = fixture.deserialize(new ByteArrayInputStream(serializedDeserProxy));
        var cloneResult = fixture.deepClone(deserializationProxy);
        assertThat(cloneResult).isInstanceOf(Integer.class).isNotSameAs(obj1).isEqualTo(obj1);
    }

    @Test
    void testDeepCloneInternalProxyTrue() {
        var expected = new byte[] { -84, -19, 0, 5, 115, 114, 0, 3, 120, 112, 0, 112, 117, 114, 0, 4, 120, 112, 0, 0, 0, 18, -84, -19, 0, 5, 115, 114, 0, 1, 120, 114, 0, 2, 120, 112, 0, 0, 0, 1 };
        var deserializationProxy = fixture.deserialize(new ByteArrayInputStream(serializedDeserProxy));
        var cloneResult = fixture.deepCloneInternal(deserializationProxy, true);
        assertThat(cloneResult).isInstanceOf(DeserializationProxy.class);
        assertThat(((DeserializationProxy) cloneResult).getOriginalType()).isNull();
        assertThat(((DeserializationProxy) cloneResult).getSerializedValue()).containsExactly(expected);
    }

    @Test
    void testDeepCloneInternalJsonFalse() {
        var cloneResult = fixture.deepClone(json1);
        assertThat(cloneResult).isInstanceOf(JsonStruct.class).isNotSameAs(json1).isEqualTo(json1);
    }

    @Test
    void testDeepCloneInternalJsonTrue() {
        var expected = "{\"myField\":1}";
        var cloneResult = fixture.deepCloneInternal(json1, true);
        assertThat(cloneResult).isInstanceOf(DeserializationProxy.class);
        assertThat(((DeserializationProxy) cloneResult).getOriginalType()).isEqualTo(JsonStruct.class);
        assertThat(((DeserializationProxy) cloneResult).getSerializedValue()).containsExactly(expected.getBytes());
    }
}
