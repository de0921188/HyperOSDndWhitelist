package com.qing.dnd.whitelist;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Small before/after adapter over the modern interceptor; no legacy API dependency. */
final class HookRunner {
    interface Original { Object proceed() throws Throwable; }
    abstract static class Callback {
        protected void beforeHookedMethod(Invocation call) throws Throwable {}
        protected void afterHookedMethod(Invocation call) throws Throwable {}
    }
    static final class Invocation {
        final Object thisObject;
        final Object[] args;
        private final Map<String, Object> extras = new HashMap<>();
        private Object result;
        private Throwable failure;
        private boolean early;
        Invocation(Object target, Object[] args) { this.thisObject = target; this.args = args; }
        void setResult(Object value) { result = value; failure = null; early = true; }
        Object getResult() { return result; }
        boolean hasThrowable() { return failure != null; }
        void setObjectExtra(String key, Object value) { extras.put(key, value); }
        Object getObjectExtra(String key) { return extras.get(key); }
    }
    static Object run(Object target, Object[] args, Original original, Callback callback,
                      Consumer<Throwable> report) throws Throwable {
        Invocation call = new Invocation(target, args);
        try { callback.beforeHookedMethod(call); }
        catch (Throwable error) { call.early = false; call.failure = null; report.accept(error); }
        if (!call.early) {
            try { call.result = original.proceed(); }
            catch (Throwable error) { call.failure = error; }
        }
        Object originalResult = call.result;
        Throwable originalFailure = call.failure;
        try { callback.afterHookedMethod(call); }
        catch (Throwable error) {
            call.result = originalResult;
            call.failure = originalFailure;
            report.accept(error);
        }
        if (call.failure != null) throw call.failure;
        return call.result;
    }
}
