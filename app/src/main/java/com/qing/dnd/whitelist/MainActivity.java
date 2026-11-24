package com.qing.dnd.whitelist;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private int themeFingerprint;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> selected = new HashSet<>();
    private final Map<String, Integer> appModes = new HashMap<>();
    private final List<App> apps = new ArrayList<>();
    private final List<App> visible = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean bridgeReady;
    private int userId;
    private TextView status, count;
    private EditText search;
    private Switch master, onlySelected;
    private AppAdapter adapter;
    private boolean loading = true;
    private boolean testPending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        themeFingerprint = themeFingerprint();
        userId = android.os.Process.myUid() / 100000;
        prefs = SettingsBridge.get().preferences();
        bridgeReady = prefs != null;
        if (!bridgeReady) prefs = getSharedPreferences("preview", MODE_PRIVATE);
        selected.addAll(prefs.getStringSet("apps", Collections.emptySet()));
        appModes.putAll(AppModes.decode(prefs.getStringSet("app_modes", Collections.emptySet())));
        int legacyMode = prefs.getInt("screen_mode", 2) == 1 ? 1 : 2;
        for (String key : selected) appModes.putIfAbsent(key, legacyMode);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.page_background));
        root.setPadding(dp(20), dp(8), dp(20), 0);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(dp(20) + bars.left, dp(8) + bars.top, dp(20) + bars.right, bars.bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        TextView title = label(getString(R.string.app_name), 28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColor(R.color.page_accent));
        root.addView(title);
        root.addView(label(getString(R.string.subtitle), 16));
        status = label("", 13);
        root.addView(status);
        master = new Switch(this);
        master.setText(getString(R.string.enable));
        master.setTextSize(17);
        master.setPadding(0, dp(8), 0, dp(8));
        master.setChecked(prefs.getBoolean("enabled", false));
        master.setEnabled(bridgeReady);
        master.setOnCheckedChangeListener((button, checked) -> save());
        root.addView(master);
        root.addView(label(getString(R.string.mode_hint), 15));
        root.addView(label(getString(R.string.permission_hint), 13));
        LinearLayout actions = new LinearLayout(this);
        Button test = new Button(this);
        test.setText(getString(R.string.test_button));
        test.setOnClickListener(v -> scheduleTest());
        actions.addView(test, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button help = new Button(this);
        help.setText(getString(R.string.help_button));
        help.setOnClickListener(v -> showHelp());
        actions.addView(help, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(actions);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(getString(R.string.search_hint));
        search.setTextSize(15);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        onlySelected = new Switch(this);
        onlySelected.setText(getString(R.string.selected_only));
        onlySelected.setOnCheckedChangeListener((button, checked) -> filter());
        if (state != null) onlySelected.setChecked(state.getBoolean("only_selected", false));
        root.addView(onlySelected);
        count = label(getString(R.string.loading), 13);
        root.addView(count);
        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(getColor(R.color.page_divider)));
        list.setDividerHeight(dp(1));
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> toggle(visible.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int c, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int c) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        if (state != null) search.setText(state.getString("query", ""));
        refreshStatus();
        worker.execute(() -> {
            List<App> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            for (ApplicationInfo info : pm.getInstalledApplications(0)) {
                if (!info.enabled) continue;
                String name;
                try { name = pm.getApplicationLabel(info).toString(); }
                catch (RuntimeException ignored) { name = info.packageName; }
                result.add(new App(name, info.packageName));
            }
            Collator collator = Collator.getInstance(getResources().getConfiguration().getLocales().get(0));
            result.sort((a, b) -> collator.compare(a.name, b.name));
            main.post(() -> {
                if (isDestroyed()) return;
                apps.addAll(result);
                loading = false;
                filter();
            });
        });
    }

    private final Runnable connectionChanged = () -> {
        if (!isFinishing() && !isDestroyed()) recreate();
    };
    @Override protected void onStart() {
        super.onStart(); SettingsBridge.get().addListener(connectionChanged);
    }
    @Override protected void onStop() {
        SettingsBridge.get().removeListener(connectionChanged); super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        // Resource overlays can change while this Activity is in the background.
        if (themeFingerprint != themeFingerprint()) { recreate(); return; }
        if (status != null) refreshStatus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", search.getText().toString());
        state.putBoolean("only_selected", onlySelected.isChecked());
        super.onSaveInstanceState(state);
    }

    private int themeFingerprint() {
        return java.util.Arrays.hashCode(new int[]{getColor(R.color.page_background),
                getColor(R.color.page_text), getColor(R.color.page_secondary),
                getColor(R.color.page_accent), getColor(R.color.page_button)});
    }

    private void refreshStatus() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean dnd = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        status.setText((bridgeReady ? getString(R.string.status_ready) : getString(R.string.status_unready))
                + getString(R.string.service) + (bridgeReady ? getString(R.string.connected) : getString(R.string.disconnected))
                + getString(R.string.dnd_status) + (dnd ? getString(R.string.on) : getString(R.string.off))
                + getString(R.string.status_hint));
        status.setTextColor(getColor(bridgeReady ? R.color.page_accent : R.color.page_warning));
    }

    private void toggle(App app) {
        if (!bridgeReady) { showHelp(); return; }
        String key = WhitelistPolicy.key(userId, app.pkg);
        int current = selected.contains(key) ? appModes.getOrDefault(key, 2) : 0;
        new AlertDialog.Builder(this).setTitle(app.name)
                .setSingleChoiceItems(new String[]{getString(R.string.deny), getString(R.string.allow_wake), getString(R.string.allow_edge)}, current, (dialog, mode) -> {
                    Set<String> previousSelected = new HashSet<>(selected);
                    Map<String, Integer> previousModes = new HashMap<>(appModes);
                    if (mode == 0) { selected.remove(key); appModes.remove(key); }
                    else { selected.add(key); appModes.put(key, mode); }
                    if (!save()) {
                        selected.clear(); selected.addAll(previousSelected);
                        appModes.clear(); appModes.putAll(previousModes);
                    }
                    dialog.dismiss();
                    filter();
                }).setNegativeButton(getString(R.string.cancel), null).show();
    }

    private boolean save() {
        if (!bridgeReady) return false;
        boolean saved = prefs.edit().putBoolean("enabled", master.isChecked())
                .putStringSet("app_modes", AppModes.encode(appModes))
                .putStringSet("apps", new HashSet<>(selected)).commit();
        if (!saved) Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_LONG).show();
        return saved;
    }

    private void filter() {
        if (adapter == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (App app : apps) {
            if (onlySelected.isChecked() && !selected.contains(WhitelistPolicy.key(userId, app.pkg))) continue;
            if (app.name.toLowerCase(Locale.ROOT).contains(query) || app.pkg.toLowerCase(Locale.ROOT).contains(query)) visible.add(app);
        }
        long total = selected.stream().filter(k -> k.startsWith(userId + ":")).count();
        count.setText(loading ? getString(R.string.loading) : getString(R.string.counts, total, visible.size()));
        adapter.notifyDataSetChanged();
    }

    private void showHelp() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.help_title))
                .setMessage(getString(R.string.help_text))
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton(getString(R.string.app_notification_settings), (d, w) -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())))
                .show();
    }

    private void scheduleTest() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            Toast.makeText(this, getString(R.string.permission_retry), Toast.LENGTH_LONG).show();
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("dnd_test", getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 250, 150, 250});
        nm.createNotificationChannel(channel);
        if (!nm.areNotificationsEnabled() || nm.getNotificationChannel("dnd_test").getImportance() < NotificationManager.IMPORTANCE_HIGH) {
            Toast.makeText(this, getString(R.string.channel_hint), Toast.LENGTH_LONG).show();
            return;
        }
        if (testPending) { Toast.makeText(this, getString(R.string.test_pending), Toast.LENGTH_SHORT).show(); return; }
        testPending = true;
        boolean allow = bridgeReady && master.isChecked() && selected.contains(WhitelistPolicy.key(userId, getPackageName()));
        Toast.makeText(this, getString(R.string.test_prefix) + (allow ? getString(R.string.test_allow) : getString(R.string.test_control)), Toast.LENGTH_LONG).show();
        main.postDelayed(() -> {
            testPending = false;
            Notification n = new Notification.Builder(this, "dnd_test")
                    .setSmallIcon(R.drawable.ic_module).setContentTitle(getString(R.string.test_title))
                    .setContentText(getString(R.string.test_body))
                    .setAutoCancel(true).build();
            try { nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n); }
            catch (SecurityException ignored) { /* Permission may be revoked during the delay. */ }
        }, 6000);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(R.color.page_text));
        view.setPadding(0, dp(5), 0, dp(5));
        return view;
    }
    private static final class App {
        final String name, pkg;
        App(String name, String pkg) { this.name = name; this.pkg = pkg; }
    }
    private final class AppAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int position) { return visible.get(position); }
        public long getItemId(int position) { return position; }
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout row;
            if (convert == null) {
                row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));
                LinearLayout text = new LinearLayout(MainActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                text.addView(label("", 16));
                text.addView(label("", 11));
                text.addView(label("", 13));
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                CheckBox box = new CheckBox(MainActivity.this);
                box.setClickable(false);
                box.setFocusable(false);
                row.addView(box);
            } else row = (LinearLayout) convert;
            App app = visible.get(position);
            LinearLayout text = (LinearLayout) row.getChildAt(0);
            ((TextView) text.getChildAt(0)).setText(app.name);
            ((TextView) text.getChildAt(1)).setText(app.pkg);
            ((TextView) text.getChildAt(1)).setTextColor(getColor(R.color.page_secondary));
            boolean allowed = selected.contains(WhitelistPolicy.key(userId, app.pkg));
            String modeLabel = !allowed ? getString(R.string.row_deny) : appModes.getOrDefault(WhitelistPolicy.key(userId, app.pkg), 2) == 1 ? getString(R.string.row_wake) : getString(R.string.row_edge);
            ((TextView) text.getChildAt(2)).setText(modeLabel);
            ((TextView) text.getChildAt(2)).setTextColor(getColor(allowed ? R.color.page_accent : R.color.page_secondary));
            CheckBox box = (CheckBox) row.getChildAt(1);
            box.setChecked(selected.contains(WhitelistPolicy.key(userId, app.pkg)));
            box.setEnabled(bridgeReady);
            box.setContentDescription(app.name + " " + modeLabel);
            row.setAlpha(bridgeReady ? 1f : 0.5f);
            return row;
        }
    }
}

