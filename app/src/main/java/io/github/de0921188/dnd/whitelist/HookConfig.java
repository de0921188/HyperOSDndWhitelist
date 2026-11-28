package io.github.de0921188.dnd.whitelist;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import io.github.libxposed.api.XposedModule;

final class HookConfig {
    private final XposedModule module;
    private SharedPreferences preferences;
    private long lastRead = -2000;
    volatile long revision = -1;
    volatile boolean reportedError;
    private WhitelistPolicy snapshot = new WhitelistPolicy(false, Collections.emptySet());
    HookConfig(XposedModule module) { this.module = module; }
    synchronized WhitelistPolicy get() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastRead < 1000) return snapshot;
        lastRead = now;
        try {
            if (preferences == null) preferences = module.getRemotePreferences("config");
            Map<String, ?> values = preferences.getAll();
            int mode = WhitelistPolicy.legacyMode(values.get("screen_mode"));
            snapshot = new WhitelistPolicy(Boolean.TRUE.equals(values.get("enabled")), strings(values.get("apps")), mode,
                    AppModes.decode(strings(values.get("app_modes"))), strings(values.get("channels")),
                    strings(values.get("no_sound")), strings(values.get("no_vibration")), strings(values.get("channel_options")));
            revision = values.get("revision") instanceof Long r ? r : 0L;
            reportedError = false;
        } catch (Throwable error) {
            snapshot = new WhitelistPolicy(false, Collections.emptySet()); preferences = null;
            if (!reportedError) {
                reportedError = true;
                module.log(Log.ERROR, "DndWhitelist", "Remote preferences unavailable; bypass disabled", error);
            }
        }
        return snapshot;
    }
    private static Set<String> strings(Object value) {
        if (value == null) return Collections.emptySet();
        if (!(value instanceof Set<?>)) throw new IllegalArgumentException("Invalid preference set");
        Set<String> result = new HashSet<>();
        for (Object item : (Set<?>) value) {
            if (!(item instanceof String)) throw new IllegalArgumentException("Invalid preference entry");
            result.add((String) item);
        }
        return result;
    }
}
