package io.github.de0921188.dnd.whitelist;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class QuickRestart {
    private static final String[] PACKAGES = {
            "com.android.systemui", "com.miui.home", "com.android.settings", "com.miui.securitycenter"
    };
    private final MainActivity activity;
    private boolean running;
    QuickRestart(MainActivity activity) { this.activity = activity; }

    void show() {
        if (running) return;
        int[] resources = {R.string.restart_ui,R.string.restart_home,R.string.restart_settings,R.string.restart_security};
        List<String> labels = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < PACKAGES.length; i++) {
            try {
                activity.getPackageManager().getApplicationInfo(PACKAGES[i],0);
                targets.add(PACKAGES[i]); labels.add(activity.getString(resources[i]));
            } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        }
        boolean[] checked = new boolean[targets.size()];
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.quick_restart)
                .setMultiChoiceItems(labels.toArray(new String[0]),checked,(d,index,on) -> checked[index] = on)
                .setNeutralButton(R.string.select_all,null)
                .setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.restart_confirm,null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                java.util.Arrays.fill(checked,true);
                for (int i = 0; i < checked.length; i++) dialog.getListView().setItemChecked(i,true);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<String> selected = new ArrayList<>();
                for (int i = 0; i < checked.length; i++) if (checked[i]) selected.add(targets.get(i));
                if (selected.isEmpty()) return;
                dialog.dismiss();
                execute(selected);
            });
        });
        dialog.show();
    }

    private void execute(List<String> selected) {
        running = true;
        Toast.makeText(activity,R.string.restart_running,Toast.LENGTH_SHORT).show();
        // Only fixed package names from the installed-target list enter this command.
        StringBuilder command = new StringBuilder("set -e\n");
        for (String pkg : selected) {
            if (pkg.equals("com.android.systemui")) {
                command.append("for task_pid in $(pidof com.android.systemui); do ")
                        .append("case \"$task_pid\" in *[!0-9]*|'') exit 1;; esac; kill -TERM \"$task_pid\"; done\n");
            } else {
                command.append("am force-stop --user ").append(android.os.Process.myUid() / 100000)
                        .append(" ").append(pkg).append("\n");
                if (pkg.equals("com.miui.home"))
                    command.append("am start --user ").append(android.os.Process.myUid() / 100000)
                            .append(" -a android.intent.action.MAIN -c android.intent.category.HOME\n");
            }
        }
        new Thread(() -> {
            Process process = null;
            boolean success = false;
            try {
                process = new ProcessBuilder("su","-c",command.toString())
                        .redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .redirectError(ProcessBuilder.Redirect.to(new java.io.File("/dev/null"))).start();
                success = process.waitFor(45,TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception e) {
                android.util.Log.w("DndWhitelist","Quick restart failed",e);
            } finally { if (process != null) process.destroy(); }
            boolean completed = success;
            new Handler(Looper.getMainLooper()).post(() -> {
                running = false;
                if (!activity.isDestroyed()) Toast.makeText(activity,
                        completed ? R.string.restart_done : R.string.restart_failed,Toast.LENGTH_LONG).show();
            });
        },"DndWhitelist-restart").start();
    }
}
