package io.github.de0921188.dnd.whitelist;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

/** Process-local state. Diagnostics carry hook names and counters only. */
final class RuntimeStatus {
    private static final String PACKAGE = "io.github.de0921188.dnd.whitelist";
    static final String QUERY = PACKAGE + ".QUERY_HOOKS";
    static final String PERMISSION = PACKAGE + ".permission.DIAGNOSTICS";
    private final ConcurrentSkipListMap<String, Integer> hooks = new ConcurrentSkipListMap<>();
    volatile boolean dnd;
    private int callbacks, samples, errors;
    private long sampleNanos;
    synchronized long beginSample() {
        return (++callbacks & 63) == 1 ? System.nanoTime() : 0;
    }
    synchronized void finishSample(long start) {
        if (start != 0) { samples++; sampleNanos += System.nanoTime() - start; }
    }
    synchronized boolean reportError() {
        errors++;
        return errors == 1 || (errors & (errors - 1)) == 0;
    }
    void hook(String name, int count) { hooks.put(name, count); }

    void start(boolean server, java.util.function.Consumer<Throwable> report) {
        if (Looper.getMainLooper() == null) {
            Thread bootstrap = new Thread(() -> {
                for (int i = 0; i < 100 && Looper.getMainLooper() == null; i++) android.os.SystemClock.sleep(100);
                if (Looper.getMainLooper() != null) start(server, report);
                else report.accept(new IllegalStateException("Main looper unavailable"));
            }, "DndWhitelist-init");
            bootstrap.start(); return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            int attempts;
            public void run() {
                try {
                    Class<?> thread = Class.forName("android.app.ActivityThread");
                    Application app = (Application) thread.getMethod("currentApplication").invoke(null);
                    Context context = app;
                    if (server && context == null) {
                        Object current = thread.getMethod("currentActivityThread").invoke(null);
                        context = (Context) thread.getMethod("getSystemContext").invoke(current);
                    }
                    if (context == null) throw new IllegalStateException("Context not ready");
                    attach(context, handler, server);
                } catch (Exception e) {
                    if (++attempts < 6) handler.postDelayed(this, 1000L * attempts);
                    else report.accept(e);
                }
            }
        });
    }
    private void attach(Context context, Handler handler, boolean server) {
        android.content.ContentResolver resolver = context.getContentResolver();
        ContentObserver observer = new ContentObserver(handler) {
            public void onChange(boolean self) {
                dnd = Settings.Global.getInt(resolver, "zen_mode", 0) != 0;
            }
        };
        resolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, observer);
        try {
            observer.onChange(false);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                public void onReceive(Context ctx, Intent intent) {
                    Bundle result = new Bundle();
                    hooks.forEach(result::putInt);
                    synchronized (RuntimeStatus.this) {
                        result.putInt("_errors", errors);
                        result.putInt("_samples", samples);
                        result.putLong("_us", samples == 0 ? 0 : sampleNanos / samples / 1000);
                    }
                    String pkg = intent.getStringExtra("package");
                    if (server && pkg != null) {
                        try {
                            int user = getSendingUserIdCompat(this);
                            android.content.pm.PackageManager pm = ((Context) Context.class.getMethod("createContextAsUser", android.os.UserHandle.class, int.class)
                                    .invoke(ctx, android.os.UserHandle.getUserHandleForUid(user * 100000), 0)).getPackageManager();
                            int uid = pm.getApplicationInfo(pkg, 0).uid;
                            Object manager = NotificationManager.class.getMethod("getService").invoke(null);
                            Object slice = Class.forName("android.app.INotificationManager")
                                    .getMethod("getNotificationChannelsForPackage", String.class, int.class, boolean.class)
                                    .invoke(manager, pkg, uid, false);
                            Object list = slice instanceof List ? slice : slice.getClass().getMethod("getList").invoke(slice);
                            ArrayList<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                            int bytes = 0;
                            for (Object obj : (List<?>) list) {
                                NotificationChannel ch = (NotificationChannel) obj;
                                if (ch.getParentChannelId() != null) continue;
                                String name = String.valueOf(ch.getName());
                                bytes += 2 * (name.length() + ch.getId().length());
                                if (bytes > 96000) break;
                                ids.add(ch.getId()); labels.add(name);
                            }
                            result.putStringArrayList("_ids", ids);
                            result.putStringArrayList("_labels", labels);
                        } catch (Exception ignored) { /* Manual channel IDs remain available. */ }
                    }
                    setResultExtras(result);
                }
            };
            IntentFilter filter = new IntentFilter(QUERY);
            context.registerReceiver(receiver, filter, PERMISSION, handler,
                    Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_EXPORTED : 0);
        } catch (RuntimeException e) {
            resolver.unregisterContentObserver(observer); dnd = false; throw e;
        }
    }
    private static int getSendingUserIdCompat(BroadcastReceiver receiver) throws Exception {
        return (int) BroadcastReceiver.class.getMethod("getSendingUserId").invoke(receiver);
    }
}
