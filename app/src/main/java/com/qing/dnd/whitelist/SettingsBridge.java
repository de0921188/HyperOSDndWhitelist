package com.qing.dnd.whitelist;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.util.Log;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class SettingsBridge extends Application implements XposedServiceHelper.OnServiceListener {
    private static SettingsBridge instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners = new HashSet<>();
    private XposedService service;
    private SharedPreferences preferences;
    private String status = "尚未連上現代 Xposed 設定服務";

    public static SettingsBridge get() { return instance; }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { connect(); }
        }, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        XposedServiceHelper.registerListener(this);
    }
    @Override public void onServiceBind(XposedService value) {
        main.post(() -> { service = value; connect(); });
    }
    @Override public void onServiceDied(XposedService value) {
        main.post(() -> {
            if (service == value) { service = null; preferences = null; status = "設定服務已中斷，請重新開啟 App"; notifyListeners(); }
        });
    }
    private void connect() {
        if (service == null || !getSystemService(UserManager.class).isUserUnlocked()) return;
        try {
            SharedPreferences remote = service.getRemotePreferences("config");
            SharedPreferences pending = getSharedPreferences("migration_pending", MODE_PRIVATE);
            if (pending.getBoolean("_requested", false)) {
                Map<String, ?> source = pending.getAll();
                SharedPreferences.Editor edit = remote.edit().clear();
                for (Map.Entry<String, ?> entry : source.entrySet()) {
                    String key = entry.getKey(); Object value = entry.getValue();
                    if (key.equals("_requested")) continue;
                    if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                    else if (value instanceof Long) edit.putLong(key, (Long) value);
                    else if (value instanceof Float) edit.putFloat(key, (Float) value);
                    else if (value instanceof String) edit.putString(key, (String) value);
                    else if (value instanceof Set<?>) {
                        Set<String> strings = new HashSet<>();
                        for (Object item : (Set<?>) value) {
                            if (!(item instanceof String)) throw new IllegalArgumentException("Invalid migration set");
                            strings.add((String) item);
                        }
                        edit.putStringSet(key, strings);
                    } else throw new IllegalArgumentException("Unsupported migration value");
                }
                if (!edit.commit()) throw new IllegalStateException("Migration write failed");
                Map<String, ?> actual = remote.getAll();
                for (Map.Entry<String, ?> entry : source.entrySet()) {
                    if (!entry.getKey().equals("_requested") && !entry.getValue().equals(actual.get(entry.getKey())))
                        throw new IllegalStateException("Migration verification failed");
                }
                if (!pending.edit().clear().putBoolean("_done", true).commit())
                    throw new IllegalStateException("Migration acknowledgement failed");
                Log.i("DndWhitelist", "Settings migration committed and verified");
            }
            preferences = remote;
            status = service.getFrameworkName() + " 現代設定服務已連線";
        } catch (RuntimeException error) {
            preferences = null;
            status = "設定服務或遷移失敗，請保留舊資料並重新開啟 App";
            Log.e("DndWhitelist", "Modern preferences unavailable", error);
        }
        notifyListeners();
    }
    SharedPreferences preferences() { return preferences; }
    String status() { return status; }
    void addListener(Runnable listener) { listeners.add(listener); }
    void removeListener(Runnable listener) { listeners.remove(listener); }
    private void notifyListeners() {
        for (Runnable listener : new HashSet<>(listeners)) listener.run();
    }
}
