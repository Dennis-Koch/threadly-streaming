package org.threadlys.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface ReflectUtil {

    Field[] getAllDeclaredFields(Class<?> type);

    <T> T getFieldValue(Field field, Object obj);

    void setFieldValue(Field field, Object obj, Object value);

    Object invokeMethod(Method method, Object target, Object[] args);
}
