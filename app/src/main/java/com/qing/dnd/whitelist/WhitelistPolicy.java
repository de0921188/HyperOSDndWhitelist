package com.qing.dnd.whitelist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/** Immutable snapshot; user ID prevents the same package in another profile from leaking through. */
public final class WhitelistPolicy {
    public static final int VISUAL_MASK = 8 | 16 | 32 | 64 | 128 | 256;
    private final boolean enabled;
    private final Set<String> apps;
    private final int screenMode;
    private final Map<String, Integer> appModes;

    public WhitelistPolicy(boolean enabled, Set<String> apps) {
        this(enabled, apps, false);
    }

    public WhitelistPolicy(boolean enabled, Set<String> apps, boolean wakeScreen) {
        this(enabled, apps, wakeScreen ? 1 : 0);
    }
    public WhitelistPolicy(boolean enabled, Set<String> apps, int screenMode) {
        this(enabled, apps, screenMode, Collections.emptyMap());
    }
    public WhitelistPolicy(boolean enabled, Set<String> apps, int screenMode, Map<String, Integer> appModes) {
        this.enabled = enabled;
        this.screenMode = screenMode == 1 || screenMode == 2 ? screenMode : 0;
        this.apps = Collections.unmodifiableSet(new HashSet<>(apps));
        this.appModes = Collections.unmodifiableMap(new HashMap<>(appModes));
    }

    public boolean allowsWake(int userId, String packageName) {
        return screenModeFor(userId, packageName) == 1;
    }

    public int screenModeFor(int userId, String packageName) {
        if (!allows(userId, packageName)) return 0;
        Integer mode = appModes.get(key(userId, packageName));
        return mode != null && (mode == 1 || mode == 2) ? mode : screenMode;
    }

    public static String key(int userId, String packageName) { return userId + ":" + packageName; }

    public boolean allows(int userId, String packageName) {
        return enabled && userId >= 0 && packageName != null && !packageName.isEmpty()
                && apps.contains(key(userId, packageName));
    }

    public int visualEffects(int userId, String packageName, int effects) {
        return allows(userId, packageName) ? effects & ~VISUAL_MASK : effects;
    }

    public static int vibrationRingerMode(boolean bypassDnd, int external, int internal) {
        // DND may make only the external mode SILENT. Explicit internal SILENT remains silent.
        return bypassDnd && external == 0 && (internal == 1 || internal == 2) ? internal : external;
    }
}
