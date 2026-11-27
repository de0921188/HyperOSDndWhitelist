package io.github.de0921188.dnd.whitelist;

import android.media.AudioAttributes;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;

/** No notification re-posting, global zen changes, channel edits, or separate sound playback. */
public final class DndHook extends io.github.libxposed.api.XposedModule {
    private final HookConfig config = new HookConfig(this);
    private final DecisionScope headsUpScope = new DecisionScope();
    private final DecisionScope vibrationScope = new DecisionScope();
    private final DecisionScope wakeDecision = new DecisionScope();
    private final java.util.WeakHashMap<Object, Boolean> boundEntries = new java.util.WeakHashMap<>();
    private final NativeWakeScope nativeWake = new NativeWakeScope();

    private final RuntimeStatus runtime = new RuntimeStatus();
    private boolean serverInstalled, systemUiInstalled;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        logMessage("Modern API " + getApiVersion() + " loaded in " + param.getProcessName());
    }
    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        if (!serverInstalled) { serverInstalled = true; runtime.start(true, e -> logMessage(e.toString())); installServer(param.getClassLoader()); }
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if (!systemUiInstalled && "com.android.systemui".equals(param.getPackageName())) {
            systemUiInstalled = true; runtime.start(false, e -> logMessage(e.toString())); installSystemUi(param.getClassLoader());
        }
    }
    private void logMessage(String message) { log(android.util.Log.INFO, "DndWhitelist", message); }
    private boolean suppressPlayback(StatusBarNotification n, boolean sound) {
        return n != null && runtime.dnd && config.get().suppressPlayback(true,userId(n),n.getPackageName(),
                n.getNotification().getChannelId(),sound);
    }

    private void installPlayback(ClassLoader loader) {
        String helper = "com.android.server.notification.NotificationAttentionHelper";
        hook(loader,helper,"playSound", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) {
                if (suppressPlayback(findSbn(p.args),true)) p.setResult(false);
            }
        });
        hook(loader,helper,"playVibration", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) {
                if (suppressPlayback(findSbn(p.args),false)) p.setResult(false);
            }
        });
        // The in-call sound branch has no record argument. Bind its decision to this invocation.
        DecisionScope muteInCall = new DecisionScope();
        hook(loader,helper,"buzzBeepBlinkLocked", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) {
                p.setObjectExtra("audio.previous", muteInCall.enter(suppressPlayback(findSbn(p.args),true)));
            }
            @Override protected void afterHookedMethod(HookRunner.Invocation p) {
                Object previous = p.getObjectExtra("audio.previous");
                if (previous instanceof Boolean) muteInCall.restore((Boolean) previous);
            }
        });
        hook(loader,helper,"playInCallNotification", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) {
                if (muteInCall.active()) p.setResult(null);
            }
        });
    }

    private void installServer(ClassLoader loader) {
        installPlayback(loader);
        hook(loader, "com.android.server.notification.ZenModeFiltering", "shouldIntercept", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (selected(findSbn(p.args))) p.setResult(false);
            }
        });
        // HyperOS performs an additional quiet-mode check after the AOSP filter.
        hook(loader, "com.android.server.notification.NotificationManagerServiceImpl", "isSilenceMode", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (selected(findSbn(p.args))) p.setResult(false);
            }
        });
        String record = "com.android.server.notification.NotificationRecord";
        hook(loader, "com.android.server.notification.NotificationManagerServiceImpl", "isDeniedPlayVibration", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                boolean bypass = selected(findSbn(p.args));
                p.setObjectExtra("dnd.vibrationPrevious", vibrationScope.enter(bypass));
            }
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                Object previous = p.getObjectExtra("dnd.vibrationPrevious");
                if (previous instanceof Boolean) vibrationScope.restore((Boolean) previous);
            }
        });
        // Only within the OEM's selected-notification check; never changes AudioService state.
        hook(loader, "android.media.AudioManager", "getRingerMode", new HookRunner.Callback() {
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (!p.hasThrowable() && vibrationScope.active() && p.getResult() instanceof Integer) {
                    int internal = (Integer) Reflector.callMethod(p.thisObject, "getRingerModeInternal");
                    p.setResult(WhitelistPolicy.vibrationRingerMode(true, (Integer) p.getResult(), internal));
                }
            }
        });
        hook(loader, record, "isIntercepted", new HookRunner.Callback() {
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (!p.hasThrowable() && selected(sbn(p.thisObject))) p.setResult(false);
            }
        });
        hook(loader, record, "getSuppressedVisualEffects", new HookRunner.Callback() {
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                StatusBarNotification n = sbn(p.thisObject);
                if (!p.hasThrowable() && selected(n) && p.getResult() instanceof Integer) {
                    p.setResult(config.get().visualEffects(userId(n), n.getPackageName(), (Integer) p.getResult()));
                }
            }
        });
        hook(loader, record, "getAudioAttributes", new HookRunner.Callback() {
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (!p.hasThrowable() && p.getResult() instanceof AudioAttributes
                        && selected(sbn(p.thisObject))) {
                    AudioAttributes original = (AudioAttributes) p.getResult();
                    // FLAG_BYPASS_INTERRUPTION_POLICY, not FLAG_BYPASS_MUTE. Retain usage and volume.
                    p.setResult(new AudioAttributes.Builder(original).setFlags(1 << 6).build());
                }
            }
        });
        logMessage("DndWhitelist: system_server hook installation finished");
    }

    private void installSystemUi(ClassLoader loader) {
        new AodChannelFilter(this, config, runtime).install();
        // This ROM reads a boolean field directly. Scope its temporary override to the
        // original synchronous UI-thread callback and restore it before the next UI event.
        // The native callback retains lockscreen privacy, media/group/update and proximity checks.
        hook(loader, "com.android.systemui.statusbar.notification.collection.coordinator.AlertCoordinator$$ExternalSyntheticLambda0",
                "onViewBound", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (Looper.myLooper() != Looper.getMainLooper()) return;
                try {
                    long sample = runtime.beginSample();
                    try {
                        StatusBarNotification n = findSbn(p.args);
                        int mode = 0;
                        if (n != null) {
                            android.app.Notification notification = n.getNotification();
                            boolean summary = (notification.flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0;
                            Object entry = null;
                            for (Object arg : p.args) {
                                if (arg != null && arg.getClass().getName().equals(
                                        "com.android.systemui.statusbar.notification.collection.NotificationEntry")) {
                                    entry = arg; break;
                                }
                            }
                            boolean repeated = entry != null && boundEntries.put(entry, Boolean.TRUE) != null;
                            boolean once = (notification.flags & android.app.Notification.FLAG_ONLY_ALERT_ONCE) != 0;
                            boolean silentGroupMember = n.isGroup() && !summary && notification.getGroupAlertBehavior()
                                    == android.app.Notification.GROUP_ALERT_SUMMARY;
                            mode = config.get().notificationWakeMode(runtime.dnd, userId(n), n.getPackageName(),
                                    notification.getChannelId(), summary, repeated && once, silentGroupMember);
                        }
                        if (mode == 0 && !nativeWake.active()) return;
                        Object coordinator = Reflector.getObjectField(p.thisObject, "f$0");
                        Object lazy = Reflector.getObjectField(coordinator, "mAlertManager");
                        Object manager = Reflector.callMethod(lazy, "get");
                        Object settings = Reflector.getObjectField(manager, "mSettingsManager");
                        boolean current = Reflector.getBooleanField(settings, "wakeupForNotification");
                        boolean desired = nativeWake.enter(current, mode == WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT ? 2 : mode);
                        p.setObjectExtra("wake.settings", settings);
                        p.setObjectExtra("wake.previousDecision", wakeDecision.enter(mode == 1));
                        Reflector.setBooleanField(settings, "wakeupForNotification", desired);
                    } finally { runtime.finishSample(sample); }
                } catch (Throwable error) {
                    if (runtime.reportError()) logMessage("DndWhitelist: native wake scope unavailable: " + error);
                }
            }
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                Object settings = p.getObjectExtra("wake.settings");
                if (settings == null) return;
                try {
                    Reflector.setBooleanField(settings, "wakeupForNotification", nativeWake.exit());
                } finally {
                    Object previous = p.getObjectExtra("wake.previousDecision");
                    if (previous instanceof Boolean) wakeDecision.restore((Boolean) previous);
                }
            }
        });
        String entry = "com.android.systemui.statusbar.notification.collection.NotificationEntry";
        hook(loader, entry, "shouldSuppressVisualEffect", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (p.args.length == 1 && p.args[0] instanceof Integer
                        && (((Integer) p.args[0]) & WhitelistPolicy.VISUAL_MASK) != 0
                        && selected(sbn(p.thisObject))) p.setResult(false);
            }
        });
        // Keep the complete original heads-up decision (importance, snooze, lockscreen, etc.).
        // Only its nested HyperOS DND query sees "off", on this thread and for this entry.
        hook(loader, "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl",
                "checkHeadsUp", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                p.setObjectExtra("dnd.previous", headsUpScope.enter(selected(findSbn(p.args))));
            }
            @Override protected void afterHookedMethod(HookRunner.Invocation p) throws Throwable {
                Object previous = p.getObjectExtra("dnd.previous");
                if (previous instanceof Boolean) headsUpScope.restore((Boolean) previous);
            }
        });
        hook(loader, "com.android.systemui.statusbar.policy.ZenModeControllerExtKt", "isZenModeOn", new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation p) throws Throwable {
                if (headsUpScope.active() || wakeDecision.active()) p.setResult(false);
            }
        });
        logMessage("DndWhitelist: SystemUI hook installation finished");
    }

    // Keep the public API available on Android 12-16; no hidden UserHandle API access.
    @SuppressWarnings("deprecation")
    private static int userId(StatusBarNotification n) { return n.getUserId(); }

    private boolean selected(StatusBarNotification n) {
        return runtime.dnd && n != null && config.get().allowsNotification(true, userId(n), n.getPackageName(), n.getNotification().getChannelId());
    }

    private static StatusBarNotification findSbn(Object[] args) {
        for (Object arg : args) {
            StatusBarNotification found = sbn(arg);
            if (found != null) return found;
        }
        return null;
    }

    private static StatusBarNotification sbn(Object object) {
        if (object instanceof StatusBarNotification) return (StatusBarNotification) object;
        if (object == null) return null;
        String name = object.getClass().getName();
        // Never infer the owner from arbitrary strings or notification extras supplied by an app.
        if (!name.equals("com.android.server.notification.NotificationRecord")
                && !name.equals("com.android.systemui.statusbar.notification.collection.NotificationEntry")) return null;
        try {
            Object value = Reflector.callMethod(object, "getSbn");
            return value instanceof StatusBarNotification ? (StatusBarNotification) value : null;
        } catch (Throwable ignored) {
            try {
                Object value = Reflector.getObjectField(object, "mSbn");
                return value instanceof StatusBarNotification ? (StatusBarNotification) value : null;
            } catch (Throwable missing) { return null; }
        }
    }

    private void hook(ClassLoader loader, String className, String method, HookRunner.Callback callback) {
        try {
            Class<?> type = Reflector.findClass(className, loader);
            // Reject a wrong overload before it can return a value of the wrong type.
            int count = 0;
            for (Method candidate : type.getDeclaredMethods()) {
                if (!candidate.getName().equals(method)) continue;
                Class<?> result = candidate.getReturnType();
                if ((method.equals("onViewBound") || method.equals("playInCallNotification")) ? result != void.class
                        : method.equals("getAudioAttributes") ? result != AudioAttributes.class
                        : (method.equals("getSuppressedVisualEffects") || method.equals("getRingerMode") || method.equals("buzzBeepBlinkLocked")) ? result != int.class
                        : result != boolean.class) continue;
                hook(candidate).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(chain -> HookRunner.run(chain.getThisObject(), chain.getArgs().toArray(), chain::proceed, callback, error -> { if (runtime.reportError()) log(android.util.Log.ERROR, "DndWhitelist", "Hook callback failed", error); }));
                count++;
            }
            runtime.hook(method, count);
            logMessage("DndWhitelist: " + className + "#" + method + " hooks=" + count);
        } catch (Throwable error) {
            runtime.hook(method, 0);
            // Missing OEM hooks disable that path, rather than crashing system_server / SystemUI.
            logMessage("DndWhitelist: unavailable " + className + "#" + method + ": " + error);
        }
    }
}
