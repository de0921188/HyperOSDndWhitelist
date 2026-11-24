package com.qing.dnd.whitelist;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Preference wire format: userId:packageName=mode (1=wake, 2=edge). */
final class AppModes {
    static Map<String, Integer> decode(Set<String> values) {
        Map<String, Integer> result = new HashMap<>();
        for (String value : values) {
            if (value == null) continue;
            int split = value.lastIndexOf('=');
            if (split < 3 || split != value.length() - 2) continue;
            String key = value.substring(0, split);
            char mode = value.charAt(split + 1);
            if (mode == '1' || mode == '2') {
                Integer old = result.putIfAbsent(key, mode - '0');
                // Reject conflicting duplicate entries deterministically.
                if (old != null && old != mode - '0') result.put(key, 0);
            }
        }
        result.values().removeIf(value -> value == 0);
        return result;
    }
    static Set<String> encode(Map<String, Integer> modes) {
        Set<String> result = new HashSet<>();
        modes.forEach((key, mode) -> {
            if (mode != null && (mode == 1 || mode == 2)) result.add(key + "=" + mode);
        });
        return result;
    }
}
