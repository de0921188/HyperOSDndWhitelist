package com.qing.dnd.whitelist;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class Reflector {
    static Class<?> findClass(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
    private static Field field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    static Object getObjectField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name).get(target);
    }
    static boolean getBooleanField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name).getBoolean(target);
    }
    static void setBooleanField(Object target, String name, boolean value) throws ReflectiveOperationException {
        field(target, name).setBoolean(target, value);
    }
    static Object callMethod(Object target, String name) throws Throwable {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name); method.setAccessible(true);
                try { return method.invoke(target); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
}
