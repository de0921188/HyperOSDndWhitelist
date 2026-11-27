package io.github.de0921188.dnd.whitelist;

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
    private android.widget.ImageButton dndToggle;
    private boolean changingDnd;
    private boolean dndReceiverRegistered;
    private final android.content.BroadcastReceiver dndChanged = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, Intent intent) {
            refreshStatus();
        }
    };
    private void toggleDnd() {
        if (changingDnd) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean enable = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL;
        changingDnd = true;
        dndToggle.setEnabled(false);
        new Thread(() -> {
            Process process = null;
            boolean success = false;
            try {
                process = new ProcessBuilder("su","-c","cmd notification set_dnd " + (enable ? "priority" : "all"))
                        .redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")))
                        .redirectError(ProcessBuilder.Redirect.to(new java.io.File("/dev/null"))).start();
                success = process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
                if (success) {
                    for (int i = 0; i < 10; i++) {
                        boolean active = nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
                        if (active == enable) break;
                        android.os.SystemClock.sleep(100);
                    }
                    success = (nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL) == enable;
                }
            } catch (Exception e) {
                android.util.Log.w("DndWhitelist","DND toggle failed",e);
            } finally { if (process != null) process.destroy(); }
            boolean completed = success;
            main.post(() -> {
                changingDnd = false;
                if (isDestroyed()) return;
                refreshStatus();
                if (!completed) Toast.makeText(this,R.string.dnd_toggle_failed,Toast.LENGTH_LONG).show();
            });
        },"DndWhitelist-zen").start();
    }

    private int themeFingerprint;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> selected = new HashSet<>();
    private final Map<String, Integer> appModes = new HashMap<>();
    private final List<App> apps = new ArrayList<>();
    private final List<App> visible = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean bridgeReady, binding;
    private final Set<String> noSound = new HashSet<>(), noVibration = new HashSet<>();
    private final Set<String> channels = new HashSet<>();
    private ListView list;
    private AdvancedSettings advanced;
    private void loadSettings() {
        prefs = SettingsBridge.get().preferences();
        bridgeReady = prefs != null;
        selected.clear(); appModes.clear(); channels.clear(); noSound.clear(); noVibration.clear();
        binding = true;
        try {
            if (bridgeReady) {
                Map<String, ?> values = prefs.getAll();
                selected.addAll(prefs.getStringSet("apps", Collections.emptySet()));
                appModes.putAll(AppModes.decode(prefs.getStringSet("app_modes", Collections.emptySet())));
                int legacyMode = WhitelistPolicy.legacyMode(values.get("screen_mode"));
                for (String key : selected) appModes.putIfAbsent(key, legacyMode);
                noSound.addAll(prefs.getStringSet("no_sound", Collections.emptySet()));
                noVibration.addAll(prefs.getStringSet("no_vibration", Collections.emptySet()));
                channels.addAll(prefs.getStringSet("channels", Collections.emptySet()));
                master.setChecked(Boolean.TRUE.equals(values.get("enabled")));
            } else master.setChecked(false);
        } catch (RuntimeException failure) {
            bridgeReady = false; master.setChecked(false);
        } finally { binding = false; }
        master.setEnabled(bridgeReady && !SettingsBridge.get().saving());
        refreshStatus(); filter();
    }

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
        advanced = new AdvancedSettings(this);
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
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ImageButton restart = new android.widget.ImageButton(this);
        restart.setImageResource(R.drawable.ic_restart);
        restart.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.page_accent)));
        restart.setContentDescription(getString(R.string.quick_restart));
        restart.setTooltipText(getString(R.string.quick_restart));
        restart.setPadding(dp(12),dp(12),dp(12),dp(12));
        android.util.TypedValue buttonBackground = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,buttonBackground,true);
        restart.setBackgroundResource(buttonBackground.resourceId);
        QuickRestart quickRestart = new QuickRestart(this);
        restart.setOnClickListener(v -> quickRestart.show());
        dndToggle = new android.widget.ImageButton(this);
        dndToggle.setImageResource(R.drawable.ic_dnd_moon);
        dndToggle.setPadding(dp(12),dp(12),dp(12),dp(12));
        dndToggle.setBackgroundResource(buttonBackground.resourceId);
        dndToggle.setOnClickListener(v -> toggleDnd());
        heading.addView(dndToggle,new LinearLayout.LayoutParams(dp(48),dp(48)));
        heading.addView(restart,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(heading);
        root.addView(label(getString(R.string.subtitle), 16));
        status = label("", 13);
        root.addView(status);
        master = new Switch(this);
        master.setText(getString(R.string.enable));
        master.setTextSize(17);
        master.setPadding(0, dp(8), 0, dp(8));
        master.setOnCheckedChangeListener((button, checked) -> { if (!binding) save(); });
        root.addView(master);
        root.addView(label(getString(R.string.mode_hint), 15));
        root.addView(label(getString(R.string.permission_hint), 13));
        LinearLayout actions = new LinearLayout(this);
        Button test = new Button(this);
        test.setText(getString(R.string.test_button));
        test.setOnClickListener(v -> scheduleTest());
        actions.addView(test, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button help = new Button(this);
        help.setText(getString(R.string.more));
        help.setOnClickListener(v -> advanced.menu());
        actions.addView(help, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
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
        list = new ListView(this);
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
        loadApps(state);
    }

    private void loadApps(Bundle state) {
        loading = true;
        worker.execute(() -> {
            List<App> result = new ArrayList<>();
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> installed = Collections.emptyList();
            for (int attempt = 0; attempt < 3 && installed.isEmpty(); attempt++) {
                try { installed = pm.getInstalledApplications(0); }
                catch (RuntimeException error) { android.util.Log.w("DndWhitelist","Application list query failed",error); }
                if (installed.isEmpty()) android.os.SystemClock.sleep(500);
            }
            for (ApplicationInfo info : installed) {
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
                apps.clear();
                apps.addAll(result);
                loading = false;
                filter();
                if (state != null) list.setSelectionFromTop(state.getInt("first"), state.getInt("offset"));
            });
        });
    }

    private final Runnable connectionChanged = () -> {
        if (!isFinishing() && !isDestroyed()) loadSettings();
    };
    @Override protected void onStart() {
        super.onStart(); SettingsBridge.get().addListener(connectionChanged); loadSettings();
        registerReceiver(dndChanged,new android.content.IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED));
        dndReceiverRegistered = true;
    }
    @Override protected void onStop() {
        if (dndReceiverRegistered) { unregisterReceiver(dndChanged); dndReceiverRegistered = false; }
        SettingsBridge.get().removeListener(connectionChanged); super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();
        // Resource overlays can change while this Activity is in the background.
        if (themeFingerprint != themeFingerprint()) { recreate(); return; }
        if (status != null) refreshStatus();
        if (!loading && apps.isEmpty()) loadApps(null);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("query", search.getText().toString());
        state.putBoolean("only_selected", onlySelected.isChecked());
        state.putInt("first", list.getFirstVisiblePosition());
        state.putInt("offset", list.getChildCount() == 0 ? 0 : list.getChildAt(0).getTop());
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
        if (dndToggle != null) {
            dndToggle.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(dnd ? R.color.page_accent : R.color.page_secondary)));
            String action = getString(dnd ? R.string.disable_dnd : R.string.enable_dnd);
            dndToggle.setContentDescription(action);
            dndToggle.setTooltipText(action);
            dndToggle.setSelected(dnd);
            dndToggle.setEnabled(!changingDnd);
        }
        status.setText((bridgeReady ? getString(R.string.status_ready) : getString(R.string.status_unready))
                + getString(R.string.service) + (bridgeReady ? getString(R.string.connected) : getString(R.string.disconnected))
                + getString(R.string.dnd_status) + (dnd ? getString(R.string.on) : getString(R.string.off))
                + getString(R.string.status_hint));
        status.setTextColor(getColor(bridgeReady ? R.color.page_accent : R.color.page_warning));
    }

    private void toggle(App app) {
        if (!canEdit()) return;
        String key = WhitelistPolicy.key(userId, app.pkg);
        int modeNow = appModes.getOrDefault(key, 0);
        int current = !selected.contains(key) ? 0 : modeNow == 0 ? 3 : modeNow == 3 ? 4 : modeNow;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), 0, dp(16), 0);
        android.widget.RadioGroup modes = new android.widget.RadioGroup(this);
        String[] labels = {getString(R.string.deny), getString(R.string.allow_wake),
                getString(R.string.allow_edge), getString(R.string.system_mode), getString(R.string.allow_dark)};
        int[] modeIds = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            android.widget.RadioButton option = new android.widget.RadioButton(this);
            modeIds[i] = View.generateViewId();
            option.setId(modeIds[i]);
            option.setText(labels[i]);
            option.setMinHeight(dp(48));
            modes.addView(option);
        }
        modes.check(modeIds[current]);
        content.addView(modes);
        CheckBox sound = new CheckBox(this);
        sound.setText(R.string.allow_sound);
        sound.setChecked(!noSound.contains(key));
        sound.setMinHeight(dp(48));
        content.addView(sound);
        CheckBox vibration = new CheckBox(this);
        vibration.setText(R.string.allow_vibration);
        vibration.setChecked(!noVibration.contains(key));
        vibration.setMinHeight(dp(48));
        content.addView(vibration);
        sound.setEnabled(current != 0);
        vibration.setEnabled(current != 0);
        modes.setOnCheckedChangeListener((group, id) -> {
            sound.setEnabled(id != modeIds[0]);
            vibration.setEnabled(id != modeIds[0]);
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(app.name).setView(scroll)
                .setPositiveButton(R.string.confirm, (d,w) -> {
                    if (!canEdit()) return;
                    int mode = 0;
                    for (int i = 0; i < modeIds.length; i++) {
                        if (modeIds[i] == modes.getCheckedRadioButtonId()) { mode = i; break; }
                    }
                    if (mode == 0) {
                        selected.remove(key); appModes.remove(key);
                        noSound.remove(key); noVibration.remove(key);
                        channels.removeIf(rule -> rule.startsWith(key + "="));
                    } else {
                        selected.add(key); appModes.put(key, mode == 3 ? 0 : mode == 4 ? 3 : mode);
                        if (sound.isChecked()) noSound.remove(key); else noSound.add(key);
                        if (vibration.isChecked()) noVibration.remove(key); else noVibration.add(key);
                    }
                    save();
                    filter();
                })
                .setNeutralButton(R.string.channels, (d,w) -> advanced.channels(key, app.pkg,
                        selected.contains(key) ? R.string.confirm : R.string.allow_save))
                .setNegativeButton(R.string.cancel, null).show();
    }

    boolean canEdit() {
        if (SettingsBridge.get().saving()) {
            Toast.makeText(this, R.string.serial_busy, Toast.LENGTH_SHORT).show(); return false;
        }
        if (!bridgeReady || prefs != SettingsBridge.get().preferences()) { loadSettings(); return false; }
        return true;
    }
    void save() {
        if (!canEdit()) return;
        master.setEnabled(false);
        SettingsBridge.get().persist(prefs, master.isChecked(), new HashSet<>(selected),
                AppModes.encode(appModes), new HashSet<>(channels), new HashSet<>(noSound), new HashSet<>(noVibration));
    }
    Set<String> channelIds(String key) { return ProfileBackup.ids(channels, key); }
    void updateChannels(String key, Set<String> ids) {
        if (!canEdit()) return;
        if (selected.add(key)) appModes.put(key, 0);
        channels.removeIf(rule -> rule.startsWith(key + "="));
        for (String id : ids) channels.add(key + "=" + id);
        save();
    }
    ProfileBackup snapshot() {
        return ProfileBackup.capture(userId, master.isChecked(), selected, appModes, channels, noSound, noVibration);
    }
    void merge(ProfileBackup backup) {
        if (!canEdit()) return;
        backup.modes.forEach((pkg, mode) -> {
            String key = WhitelistPolicy.key(userId, pkg);
            selected.add(key); appModes.put(key, mode);
            if (backup.noSound.contains(pkg)) noSound.add(key); else noSound.remove(key);
            if (backup.noVibration.contains(pkg)) noVibration.add(key); else noVibration.remove(key);
            channels.removeIf(rule -> rule.startsWith(key + "="));
            for (String id : backup.channels.get(pkg)) channels.add(key + "=" + id);
        });
        binding = true; master.setChecked(backup.enabled); binding = false;
        save();
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result == RESULT_OK && intent != null && intent.getData() != null) advanced.file(request, intent.getData());
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

    void showHelp() {
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
        NotificationChannel actual = nm.getNotificationChannel("dnd_test");
        if (!nm.areNotificationsEnabled() || actual == null || actual.getImportance() < NotificationManager.IMPORTANCE_HIGH || actual.getSound() == null || !actual.shouldVibrate()) {
            new AlertDialog.Builder(this).setMessage(R.string.channel_hint).setPositiveButton(R.string.app_notification_settings, (d,w) -> startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,"dnd_test"))).setNegativeButton(R.string.cancel,null).show();
            return;
        }
        if (testPending) { Toast.makeText(this, getString(R.string.test_pending), Toast.LENGTH_SHORT).show(); return; }
        String key = WhitelistPolicy.key(userId, getPackageName());
        Set<String> ids = channelIds(key);
        boolean allow = bridgeReady && master.isChecked() && selected.contains(key) && (ids.isEmpty() || ids.contains("dnd_test"));
        boolean dnd = nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        new AlertDialog.Builder(this).setTitle(R.string.test_title)
                .setMessage(getString(R.string.test_info, getString(R.string.app_name),
                        getString(allow && dnd ? R.string.test_allow : R.string.test_control), getString(dnd ? R.string.on : R.string.off)))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.test_button,(d,w) -> postTest(nm)).show();
    }
    private void postTest(NotificationManager nm) {
        if (testPending) return;
        testPending = true;
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
            String modeLabel = !allowed ? getString(R.string.row_deny) : getString(switch (appModes.getOrDefault(WhitelistPolicy.key(userId, app.pkg), 0)) { case 1 -> R.string.row_wake; case 2 -> R.string.row_edge; case 3 -> R.string.row_dark; default -> R.string.system_row; });
            int restricted = channelIds(WhitelistPolicy.key(userId, app.pkg)).size();
            if (allowed && restricted > 0) modeLabel += getString(R.string.rule_count, restricted);
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
