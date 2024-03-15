package org.threadlys.utils;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.SneakyThrows;

//CHECKSTYLE: IllegalCatch OFF
//CHECKSTYLE: JavadocMethod OFF
@SuppressWarnings({ "checkstyle:IllegalCatch", "checkstyle:JavadocMethod", "PMD.OptimizableToArrayCall" })
@Component
public class ReflectUtilImpl implements ReflectUtil {
    private final ConcurrentHashMap<String, Reference<Field[]>> classNameToFieldsMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Set<String>> classNameToInaccessibleFieldMap = new ConcurrentHashMap<>();

    @Override
    public Field[] getAllDeclaredFields(Class<?> type) {
        if (type == null) {
            return null;
        }
        String className = type.getName();
        Reference<Field[]> fieldsR = classNameToFieldsMap.get(className);
        Field[] fields = fieldsR != null ? fieldsR.get() : null;
        if (fields == null) {
            List<Field> fieldList = new ArrayList<>();
            buildFields(type, fieldList, field -> !Modifier.isStatic(field.getModifiers()));
            fields = fieldList.toArray(new Field[fieldList.size()]);
        }
        classNameToFieldsMap.put(className, new WeakReference<>(fields));
        return fields;
    }

    private void buildFields(Class<?> type, List<Field> fields, Function<Field, Boolean> fieldFilter) {
        if (type == null) {
            return;
        }
        Field[] declaredFields = type.getDeclaredFields();
        if (declaredFields == null) {
            return;
        }
        for (Field declaredField : declaredFields) {
            if (fieldFilter != null && !Boolean.TRUE.equals(fieldFilter.apply(declaredField))) {
                continue;
            }
            Set<String> inaccessibleFieldsSet = classNameToInaccessibleFieldMap.get(type.getName());
            if (inaccessibleFieldsSet != null && inaccessibleFieldsSet.contains(declaredField.getName())) {
                continue;
            }
            try {
                declaredField.setAccessible(true);
            } catch (InaccessibleObjectException e) {
                inaccessibleFieldsSet = classNameToInaccessibleFieldMap.computeIfAbsent(type.getName(), typeName -> new HashSet<>());
                synchronized (inaccessibleFieldsSet) {
                    inaccessibleFieldsSet.add(declaredField.getName());
                }
            }
            fields.add(declaredField);
        }
        buildFields(type.getSuperclass(), fields, fieldFilter);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public <T> T getFieldValue(Field field, Object obj) {
        return (T) field.get(obj);
    }

    @SneakyThrows
    @Override
    public void setFieldValue(Field field, Object obj, Object value) {
        field.set(obj, value);
    }

    @SneakyThrows
    @Override
    public Object invokeMethod(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
