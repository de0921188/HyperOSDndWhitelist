package io.github.de0921188.dnd.whitelist;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedModule;

/** HyperOS AOD runs as a plugin inside SystemUI with its own class loader. */
final class AodChannelFilter {
    private static final String CONTROLLER = "com.miui.aod.notification.NotificationController";
    private final DndHook module;
    private final HookConfig config;
    private final RuntimeStatus runtime;
    private final Set<ClassLoader> attached = new HashSet<>();
    private final Map<String, Meta> snapshot = new HashMap<>();

    private static final class Meta {
        final long post;
        final int user;
        final String pkg, channel;
        final boolean summary, once, quietChild;
        @SuppressWarnings("deprecation")
        Meta(StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            post = sbn.getPostTime();
            user = sbn.getUserId();
            pkg = sbn.getPackageName();
            channel = n.getChannelId();
            summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            once = (n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0;
            quietChild = sbn.isGroup() && !summary && n.getGroupAlertBehavior() == Notification.GROUP_ALERT_SUMMARY;
        }
    }
    AodChannelFilter(DndHook module, HookConfig config, RuntimeStatus runtime) {
        this.module = module; this.config = config; this.runtime = runtime;
    }
    void install() {
        try {
            Method read = NotificationListenerService.class.getMethod("getActiveNotifications");
            module.hook(read).setExceptionMode(XposedModule.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                Object value = chain.proceed();
                Object service = chain.getThisObject();
                if (service != null && value instanceof StatusBarNotification[] entries
                        && service.getClass().getName().startsWith(CONTROLLER + "$")) {
                    try { capture(service.getClass().getClassLoader(), entries); }
                    catch (ReflectiveOperationException | RuntimeException e) { report(e); }
                }
                return value;
            });
            runtime.hook("AOD notification listener",1);
        } catch (ReflectiveOperationException | RuntimeException e) { report(e); }
    }
    private synchronized void capture(ClassLoader loader, StatusBarNotification[] entries) throws ReflectiveOperationException {
        if (attached.add(loader)) {
            Class<?> task = Class.forName(CONTROLLER + "$QueryTask",false,loader);
            Class<?> wrap = Class.forName(CONTROLLER + "$NotificationWrap",false,loader);
            Method publish = task.getDeclaredMethod("onPostExecute",wrap);
            module.hook(publish).setExceptionMode(XposedModule.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                try {
                    Object request = chain.getThisObject();
                    if (runtime.dnd && Reflector.getBooleanField(request,"mNeedAnimate")
                            && allChangesSuppressed(request,chain.getArgs().get(0))) {
                        Reflector.setBooleanField(request,"mNeedAnimate",false);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) { report(e); }
                return chain.proceed();
            });
            runtime.hook("AOD channel animation",1);
            module.log(Log.INFO,"DndWhitelist","AOD channel animation hook loaded");
        }
        snapshot.clear();
        for (StatusBarNotification entry : entries) if (entry != null) snapshot.put(entry.getKey(),new Meta(entry));
    }
    private synchronized boolean allChangesSuppressed(Object task,Object current) throws ReflectiveOperationException {
        if (current == null) return false;
        Object controller = Reflector.getObjectField(task,"this$0");
        Object old = Reflector.getObjectField(controller,"mLastNotificationWrap");
        List<?> before = old == null ? Collections.emptyList() : list(old);
        Set<String> seen = new HashSet<>();
        for (Object item : before) seen.add((String) Reflector.getObjectField(item,"mKey"));
        WhitelistPolicy policy = config.get();
        boolean changed = false;
        for (Object item : list(current)) {
            if (before.contains(item)) continue; // Same equality semantics as the ROM.
            changed = true;
            String key = (String) Reflector.getObjectField(item,"mKey");
            Meta meta = snapshot.get(key);
            if (meta == null || meta.post != (Long) Reflector.getObjectField(item,"mPostTime")) return false;
            int mode = policy.notificationWakeMode(true,meta.user,meta.pkg,meta.channel,
                    meta.summary,meta.once && seen.contains(key),meta.quietChild);
            if (mode != WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT) return false;
        }
        return changed;
    }
    private List<?> list(Object wrap) throws ReflectiveOperationException {
        return (List<?>) Reflector.getObjectField(wrap,"mNotifications");
    }
    private void report(Throwable e) {
        if (runtime.reportError()) module.log(Log.WARN,"DndWhitelist","AOD channel filter unavailable",e);
    }
}
