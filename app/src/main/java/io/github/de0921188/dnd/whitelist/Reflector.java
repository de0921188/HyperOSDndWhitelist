package io.github.de0921188.dnd.whitelist;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class Reflector {
    // The key includes the actual runtime class (and therefore its class loader).
    private static final java.util.concurrent.ConcurrentMap<Class<?>, java.util.concurrent.ConcurrentMap<String, Field>> fields = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Class<?>, java.util.concurrent.ConcurrentMap<String, Method>> methods = new java.util.concurrent.ConcurrentHashMap<>();
    static Class<?> findClass(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
    private static Field field(Object target, String name) throws ReflectiveOperationException {
        var cache = fields.computeIfAbsent(target.getClass(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        Field cached = cache.get(name);
        if (cached != null) return cached;
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); cache.put(name,field); return field; }
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
        var cache = methods.computeIfAbsent(target.getClass(), ignored -> new java.util.concurrent.ConcurrentHashMap<>());
        Method method = cache.get(name);
        if (method == null) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try { method = type.getDeclaredMethod(name); break; }
                catch (NoSuchMethodException ignored) {}
            }
            if (method == null) throw new NoSuchMethodException(name);
            method.setAccessible(true);
            cache.put(name, method);
        }
        try { return method.invoke(target); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
