package io.github.de0921188.dnd.whitelist;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Looper;
import java.util.List;
import org.json.JSONArray;

/** Standalone read-only fallback launched only when the user selects the root option. */
public final class ChannelReader {
    static String state(NotificationChannel ch) { return ch.getImportance()+","+(ch.getSound()!=null ? 1 : 0)+","+(ch.shouldVibrate()?1:0); }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        if (args.length != 2) throw new IllegalArgumentException();
        Object service = NotificationManager.class.getMethod("getService").invoke(null);
        Object slice = Class.forName("android.app.INotificationManager")
                .getMethod("getNotificationChannelsForPackage", String.class, int.class, boolean.class)
                .invoke(service, args[0], Integer.parseInt(args[1]), false);
        List<?> list = slice instanceof List ? (List<?>)slice :
                (List<?>)slice.getClass().getMethod("getList").invoke(slice);
        JSONArray rows = new JSONArray();
        for (Object item : list) {
            NotificationChannel channel = (NotificationChannel)item;

                rows.put(new JSONArray().put(channel.getId()).put(String.valueOf(channel.getName())).put(channel.getParentChannelId()).put(state(channel)));
        }
        System.out.println(rows);
    }
}
