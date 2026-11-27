package io.github.de0921188.dnd.whitelist;

/** Root entry point using the same user-action semantics as the system DND toggle. */
public final class DndCommand {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("0") || args[0].equals("1"))) {
            throw new IllegalArgumentException("Expected zen mode");
        }
        Object service = android.app.NotificationManager.class.getMethod("getService").invoke(null);
        Class<?> manager = Class.forName("android.app.INotificationManager");
        try {
            manager.getMethod("setZenMode",int.class,android.net.Uri.class,String.class,boolean.class)
                    .invoke(service,Integer.parseInt(args[0]),null,"DndWhitelist user toggle",false);
        } catch (NoSuchMethodException e) {
            manager.getMethod("setZenMode",int.class,android.net.Uri.class,String.class)
                    .invoke(service,Integer.parseInt(args[0]),null,"DndWhitelist user toggle");
        }
    }
}
