package io.github.de0921188.dnd.whitelist;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/** Immutable snapshot; user ID prevents the same package in another profile from leaking through. */
public final class WhitelistPolicy {
    public static final int VISUAL_MASK = 8 | 16 | 32 | 64 | 128 | 256;
    private final Set<String> channelRules;
    private final Set<String> restrictedApps;
    private final Set<String> noSound, noVibration;
    private final boolean enabled;
    boolean suppressPlayback(boolean dnd, int user, String pkg, String channel, boolean sound) {
        return allowsNotification(dnd,user,pkg,channel) && (sound ? noSound : noVibration).contains(key(user,pkg));
    }
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
        this(enabled, apps, screenMode, appModes, Collections.emptySet());
    }

    public WhitelistPolicy(boolean enabled, Set<String> apps, int screenMode,
                           Map<String, Integer> appModes, Set<String> rules) {
        this(enabled, apps, screenMode, appModes, rules, Collections.emptySet(), Collections.emptySet());
    }

    WhitelistPolicy(boolean enabled, Set<String> apps, int screenMode, Map<String,Integer> appModes,
                    Set<String> rules, Set<String> noSound, Set<String> noVibration) {
        this.noSound = new HashSet<>(noSound);
        this.noVibration = new HashSet<>(noVibration);
        channelRules = new HashSet<>(rules);
        restrictedApps = new HashSet<>();
        for (String rule : rules) {
            int split = rule.indexOf('=');
            if (split > 0) restrictedApps.add(rule.substring(0, split));
        }
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
        return mode != null && (mode >= 0 && mode <= 3) ? mode : screenMode;
    }

    public static String key(int userId, String packageName) { return userId + ":" + packageName; }

    public boolean allows(int userId, String packageName) {
        return enabled && userId >= 0 && packageName != null && !packageName.isEmpty()
                && apps.contains(key(userId, packageName));
    }

    public boolean allowsNotification(boolean dnd, int user, String pkg, String channel) {
        if (!dnd || !allows(user, pkg)) return false;
        String key = key(user, pkg);
        return !restrictedApps.contains(key) || (channel != null && channelRules.contains(key + "=" + channel));
    }

    static final int SUPPRESS_LOCKSCREEN_ALERT = -1;

    int notificationWakeMode(boolean dnd, int user, String pkg, String channel,
                             boolean summary, boolean silentUpdate, boolean silentGroupMember) {
        if (!dnd || !allows(user, pkg)) return 0;
        if (restrictedApps.contains(key(user, pkg))
                && (!allowsNotification(true, user, pkg, channel) || summary || silentUpdate || silentGroupMember)) {
            // In this callback use the native "do not wake" field without granting a zen exception.
            return SUPPRESS_LOCKSCREEN_ALERT;
        }
        int mode = screenModeFor(user, pkg);
        return mode == 3 ? SUPPRESS_LOCKSCREEN_ALERT : mode;
    }
    static int legacyMode(Object value) {
        return value instanceof Integer && ((Integer) value == 1 || (Integer) value == 2) ? (Integer) value : 0;
    }

    public int visualEffects(int userId, String packageName, int effects) {
        return allows(userId, packageName) ? effects & ~VISUAL_MASK : effects;
    }

    public static int vibrationRingerMode(boolean bypassDnd, int external, int internal) {
        // DND may make only the external mode SILENT. Explicit internal SILENT remains silent.
        return bypassDnd && external == 0 && (internal == 1 || internal == 2) ? internal : external;
    }
}
