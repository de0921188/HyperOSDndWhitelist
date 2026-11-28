package io.github.de0921188.dnd.whitelist;

import java.util.*;
import java.nio.charset.StandardCharsets;

/** Channel IDs are opaque and may include punctuation. */
final class ChannelOptions {
    private final Map<String,String> values = new HashMap<>();
    ChannelOptions(Set<String> rows) {
        for (String row : rows) {
            int split = row.indexOf('=');
            if (split < 1) continue;
            String option = row.substring(split+1);
            if (!valid(option) || option.equals("selected")) continue;
            int last = row.lastIndexOf(':');
            values.put(row.substring(0,last),row.substring(last+1));
        }
    }
    static String encode(String id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }
    static boolean valid(String option) {
        return option.equals("selected") || option.matches("(sound|vibration):[A-Za-z0-9_-]+:[01]|mode:[A-Za-z0-9_-]+:[0-3]|parent:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+");
    }
    static String prefix(String key,String field,String id) { return key+"="+field+":"+encode(id)+":"; }
    boolean parentAllowed(String key,String channel,Set<String> rules) {
        String parent = values.get(key+"=parent:"+encode(channel));
        if (parent == null) return false;
        try { return rules.contains(key+"="+new String(Base64.getUrlDecoder().decode(parent),StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException e) { return false; }
    }
    int value(String key,String channel,String field,int fallback) {
        if (channel == null) return fallback;
        String id = encode(channel);
        String value = values.get(key+"="+field+":"+id);
        if (value == null) {
            String parent = values.get(key+"=parent:"+id);
            if (parent != null) value = values.get(key+"="+field+":"+parent);
        }
        return value == null ? fallback : Integer.parseInt(value);
    }
}
