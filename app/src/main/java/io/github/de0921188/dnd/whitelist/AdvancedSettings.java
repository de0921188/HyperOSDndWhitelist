package io.github.de0921188.dnd.whitelist;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

final class AdvancedSettings {
    private final MainActivity ui;
    private static final int EXPORT = 4801, IMPORT = 4802;
    private static final int MAX_BYTES = 1024 * 1024;
    AdvancedSettings(MainActivity ui) { this.ui = ui; }
    void menu() {
        int[] titles = {R.string.help_button,R.string.diagnostics,R.string.export_file,R.string.import_file};
        String[] items = Arrays.stream(titles).mapToObj(ui::getString).toArray(String[]::new);
        new AlertDialog.Builder(ui).setTitle(R.string.more).setItems(items,(d,which) -> {
            if (which == 0) ui.showHelp();
            else if (which == 1) diagnostics();
            else if (ui.canEdit()) {
                Intent intent = new Intent(which == 2 ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                if (which == 2) intent.putExtra(Intent.EXTRA_TITLE,"HyperOSDndWhitelist.json");
                ui.startActivityForResult(intent,which == 2 ? EXPORT : IMPORT);
            }
        }).show();
    }
    private boolean active() { return !ui.isDestroyed() && !ui.isFinishing(); }
    private void toast(int text) { if (active()) Toast.makeText(ui,text,Toast.LENGTH_LONG).show(); }
    void file(int request, Uri uri) {
        if ((request != EXPORT && request != IMPORT) || !ui.canEdit()) return;
        ProfileBackup current = ui.snapshot();
        new Thread(() -> {
            try {
                if (request == EXPORT) {
                    byte[] bytes = current.json().getBytes(StandardCharsets.UTF_8);
                    if (bytes.length > MAX_BYTES) throw new IOException("size");
                    try (OutputStream out = ui.getContentResolver().openOutputStream(uri,"wt")) {
                        Objects.requireNonNull(out).write(bytes);
                    }
                    ui.runOnUiThread(() -> toast(R.string.exported));
                } else {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = ui.getContentResolver().openInputStream(uri)) {
                        byte[] buffer = new byte[8192]; int count;
                        while ((count = Objects.requireNonNull(input).read(buffer)) != -1) {
                            if (bytes.size() + count > MAX_BYTES) throw new IOException("size");
                            bytes.write(buffer,0,count);
                        }
                    }
                    ProfileBackup backup = ProfileBackup.parse(bytes.toString("UTF-8"));
                    Set<String> missing = new TreeSet<>();
                    for (String pkg : backup.modes.keySet()) {
                        try { ui.getPackageManager().getApplicationInfo(pkg,0); }
                        catch (PackageManager.NameNotFoundException ignored) { missing.add(pkg); }
                    }
                    ui.runOnUiThread(() -> preview(backup,missing));
                }
            } catch (Exception e) { ui.runOnUiThread(() -> toast(R.string.io_error)); }
        },"DndWhitelist-backup").start();
    }
    private void preview(ProfileBackup backup, Set<String> missing) {
        if (!active() || !ui.canEdit()) return;
        ProfileBackup now = ui.snapshot();
        Set<String> conflicts = new TreeSet<>();
        int added = 0;
        for (String pkg : backup.modes.keySet()) {
            if (!now.modes.containsKey(pkg)) added++;
            else if (!now.modes.get(pkg).equals(backup.modes.get(pkg))
                    || now.noSound.contains(pkg) != backup.noSound.contains(pkg)
                    || now.noVibration.contains(pkg) != backup.noVibration.contains(pkg)
                    || !now.channels.get(pkg).equals(backup.channels.get(pkg))) conflicts.add(pkg);
        }
        String summary = ui.getString(R.string.preview,added,conflicts.size(),missing.size(),
                ui.getString(backup.enabled ? R.string.on : R.string.off));
        if (!conflicts.isEmpty()) summary += "\n\n" + String.join("\n",conflicts);
        if (!missing.isEmpty()) summary += "\n\n" + ui.getString(R.string.uninstalled) + "\n" + String.join("\n",missing);
        new AlertDialog.Builder(ui).setTitle(R.string.import_file).setMessage(summary)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.import_file,(d,w)->ui.merge(backup)).show();
    }
    private void query(String target, String pkg, Consumer<Bundle> done) {
        Intent intent = new Intent(RuntimeStatus.QUERY).setPackage(target);
        if (pkg != null) intent.putExtra("package",pkg);
        try {
            ui.sendOrderedBroadcast(intent,null,new BroadcastReceiver() {
                public void onReceive(Context ctx,Intent i) {
                    if (active()) done.accept(getResultExtras(false));
                }
            },null,0,null,null);
        } catch (RuntimeException failure) { if (active()) done.accept(null); }
    }
    private void diagnostics() {
        query("android",null,server -> query("com.android.systemui",null,sysui -> {
            StringBuilder text = new StringBuilder(ui.getString(R.string.diag_hint));
            text.append("\n\n").append(ui.getString(R.string.diag_service)).append(" · ")
                .append(ui.getString(SettingsBridge.get().preferences() == null ? R.string.disconnected : R.string.connected));
            append(text,"system_server",server); append(text,"SystemUI",sysui);
            new AlertDialog.Builder(ui).setTitle(R.string.diagnostics).setMessage(text)
                    .setPositiveButton(R.string.ok,null).show();
        }));
    }
    private void append(StringBuilder text,String process,Bundle result) {
        text.append("\n\n").append(process).append("\n");
        if (result == null || !result.containsKey("_errors")) {
            text.append(ui.getString(R.string.unknown)); return;
        }
        for (String name : new TreeSet<>(result.keySet())) if (!name.startsWith("_"))
            text.append(name).append(" · ").append(ui.getString(result.getInt(name)>0 ? R.string.found : R.string.missing)).append("\n");
        text.append(ui.getString(R.string.metrics,result.getInt("_errors"),result.getInt("_samples"),result.getLong("_us")));
    }
    void channels(String key,String pkg,int saveLabel) {
        query("android",pkg,result -> {
            Set<String> chosen = ui.channelIds(key);
            ArrayList<String> ids = result == null ? null : result.getStringArrayList("_ids");
            ArrayList<String> labels = result == null ? null : result.getStringArrayList("_labels");
            if (ids == null || labels == null || labels.size() != ids.size()) {
                new AlertDialog.Builder(ui).setTitle(R.string.channels).setMessage(R.string.query_unavailable)
                        .setPositiveButton(R.string.read_root,(d,w)->rootChannels(key,pkg,saveLabel))
                        .setNeutralButton(R.string.manual,(d,w)->manual(key,chosen,saveLabel))
                        .setNegativeButton(R.string.cancel,null).show();
                return;
            }
            choose(key,saveLabel,chosen,ids,labels);
        });
    }

    private void rootChannels(String key,String pkg,int saveLabel) {
        new Thread(() -> {
            Process process = null;
            try {
                int uid = ui.getPackageManager().getApplicationInfo(pkg,0).uid;
                String path = ui.getApplicationInfo().sourceDir;
                if (!pkg.matches("[A-Za-z0-9_.]+") || path.indexOf(39) >= 0)
                    throw new IOException("Invalid package path");
                String command = "CLASSPATH='" + path + "' /system/bin/app_process /system/bin "
                        + ChannelReader.class.getName() + " '" + pkg + "' " + uid;
                process = new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
                final Process running = process;
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                Runnable timeout = running::destroyForcibly;
                handler.postDelayed(timeout,30000);
                org.json.JSONArray rows;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line, payload = null;
                    while ((line = reader.readLine()) != null)
                        if (line.startsWith("[")) payload = line;
                    if (process.waitFor() != 0 || payload == null) throw new IOException("Query failed");
                    rows = new org.json.JSONArray(payload);
                } finally { handler.removeCallbacks(timeout); }
                ArrayList<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                for (int i=0;i<rows.length();i++) {
                    org.json.JSONArray row = rows.getJSONArray(i);
                    ids.add(row.getString(0)); labels.add(row.getString(1));
                }
                ui.runOnUiThread(() -> {
                    if (active()) choose(key,saveLabel,ui.channelIds(key),ids,labels);
                });
            } catch (Exception error) { ui.runOnUiThread(() -> toast(R.string.query_failed)); }
            finally { if (process != null) process.destroy(); }
        },"DndWhitelist-channel-query").start();
    }
    private void choose(String key,int saveLabel,Set<String> chosen,ArrayList<String> ids,ArrayList<String> labels) {
            if (ids.isEmpty() && chosen.isEmpty()) {
                new AlertDialog.Builder(ui).setTitle(R.string.channels).setMessage(R.string.no_channels)
                        .setPositiveButton(R.string.ok,null).show();
                return;
            }
            for (String id : chosen) if (!ids.contains(id)) { ids.add(id); labels.add(id); }
            boolean[] checked = new boolean[ids.size()];
            String[] choices = new String[ids.size()];
            for (int i=0;i<ids.size();i++) {
                checked[i]=chosen.contains(ids.get(i)); choices[i]=Collections.frequency(labels,labels.get(i)) > 1 ? labels.get(i)+"\n"+ids.get(i) : labels.get(i);
            }
            new AlertDialog.Builder(ui).setTitle(R.string.channel_note).setMultiChoiceItems(choices,checked,
                    (d,w,on)-> { if (on) chosen.add(ids.get(w)); else chosen.remove(ids.get(w)); })
                    .setNeutralButton(R.string.manual,(d,w)->manual(key,chosen,saveLabel))
                    .setPositiveButton(saveLabel,(d,w)->ui.updateChannels(key,chosen))
                    .setNegativeButton(R.string.cancel,null).show();
    }
    private void manual(String key,Set<String> chosen,int saveLabel) {
        EditText input = new EditText(ui); input.setMinLines(3); input.setHint(R.string.manual_hint);
        input.setText(String.join("\n",chosen));
        new AlertDialog.Builder(ui).setTitle(R.string.channels).setView(input)
                .setPositiveButton(saveLabel,(d,w)->{
                    Set<String> result = new TreeSet<>();
                    for (String id : input.getText().toString().split("\n")) if (!id.isEmpty()) result.add(id);
                    ui.updateChannels(key,result);
                }).setNegativeButton(R.string.cancel,null).show();
    }
}
