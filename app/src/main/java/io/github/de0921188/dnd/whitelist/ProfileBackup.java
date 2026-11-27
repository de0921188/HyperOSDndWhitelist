package io.github.de0921188.dnd.whitelist;

import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

/** Portable current-profile settings. Users choose the destination profile by importing there. */
final class ProfileBackup {
    boolean enabled;
    final Set<String> noSound = new TreeSet<>(), noVibration = new TreeSet<>();
    final Map<String,Integer> modes = new TreeMap<>();
    final Map<String,Set<String>> channels = new TreeMap<>();

    static ProfileBackup capture(int user, boolean enabled, Set<String> apps, Map<String,Integer> modes,
                                 Set<String> channels, Set<String> noSound, Set<String> noVibration) {
        ProfileBackup result = new ProfileBackup();
        result.enabled = enabled;
        String prefix = user + ":";
        for (String key : apps) {
            if (!key.startsWith(prefix)) continue;
            String pkg = key.substring(prefix.length());
            if (noSound.contains(key)) result.noSound.add(pkg);
            if (noVibration.contains(key)) result.noVibration.add(pkg);
            result.modes.put(pkg, modes.getOrDefault(key, 0));
            result.channels.put(pkg, ids(channels, key));
        }
        return result;
    }
    static Set<String> ids(Set<String> rules, String key) {
        Set<String> ids = new TreeSet<>();
        for (String rule : rules) if (rule.startsWith(key + "=")) ids.add(rule.substring(key.length()+1));
        return ids;
    }
    String json() throws JSONException {
        JSONArray apps = new JSONArray();
        for (String pkg : modes.keySet()) {
            apps.put(new JSONObject().put("package", pkg).put("mode", modes.get(pkg))
                    .put("sound", !noSound.contains(pkg)).put("vibration", !noVibration.contains(pkg))
                    .put("channels", new JSONArray(channels.get(pkg))));
        }
        return new JSONObject().put("format","HyperOSDndWhitelist").put("version",3)
                .put("enabled",enabled).put("apps",apps).toString(2);
    }
    static ProfileBackup parse(String text) throws JSONException {
        JSONObject data = new JSONObject(text);
        if (!"HyperOSDndWhitelist".equals(data.getString("format")) || !(Integer.valueOf(1).equals(data.get("version")) || Integer.valueOf(2).equals(data.get("version")) || Integer.valueOf(3).equals(data.get("version")))
                || !(data.get("enabled") instanceof Boolean)) throw new JSONException("format");
        ProfileBackup result = new ProfileBackup();
        result.enabled = data.getBoolean("enabled");
        JSONArray apps = data.getJSONArray("apps");
        for (int i=0; i<apps.length(); i++) {
            JSONObject row = apps.getJSONObject(i);
            String pkg = row.getString("package");
            Object value = row.get("mode");
            if (!pkg.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
                    || !(value instanceof Integer) || (int)value < 0 || (int)value > (data.getInt("version") >= 3 ? 3 : 2)
                    || result.modes.containsKey(pkg)) throw new JSONException("app");
            if (data.getInt("version") >= 2) {
                if (!(row.get("sound") instanceof Boolean) || !(row.get("vibration") instanceof Boolean))
                    throw new JSONException("playback");
                if (!row.getBoolean("sound")) result.noSound.add(pkg);
                if (!row.getBoolean("vibration")) result.noVibration.add(pkg);
            }
            result.modes.put(pkg,(Integer)value);
            JSONArray ids = row.getJSONArray("channels");
            Set<String> rules = new TreeSet<>();
            for (int k=0; k<ids.length(); k++) {
                Object id = ids.get(k);
                if (!(id instanceof String) || ((String) id).isEmpty()) throw new JSONException("channel");
                rules.add((String)id);
            }
            result.channels.put(pkg,rules);
        }
        return result;
    }
}
