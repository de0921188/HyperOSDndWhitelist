package io.github.de0921188.dnd.whitelist;
import org.junit.Test;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import static org.junit.Assert.*;

public class AppModesTest {
    @Test public void differentAppsCanUseOppositeModesWithoutInterference() {
        Set<String> apps = Set.of("0:com.example.chat", "0:io.github.de0921188.dnd.whitelist");
        Map<String, Integer> modes = Map.of("0:com.example.chat", 1, "0:io.github.de0921188.dnd.whitelist", 2);
        WhitelistPolicy p = new WhitelistPolicy(true, apps, 2, modes);
        assertEquals(1, p.screenModeFor(0, "com.example.chat"));
        assertEquals(2, p.screenModeFor(0, "io.github.de0921188.dnd.whitelist"));
        assertEquals(0, p.screenModeFor(10, "com.example.chat"));
        assertEquals(0, p.screenModeFor(0, "com.other"));
        assertEquals(modes, AppModes.decode(AppModes.encode(modes)));
    }
    @Test public void legacyModeMigratesAndOneAppOverrideDoesNotChangeOthers() {
        Set<String> apps = Set.of("0:com.a", "0:com.b");
        WhitelistPolicy old = new WhitelistPolicy(true, apps, 1);
        assertEquals(1, old.screenModeFor(0, "com.a"));
        WhitelistPolicy migrated = new WhitelistPolicy(true, apps, 1, Map.of("0:com.a", 2));
        assertEquals(2, migrated.screenModeFor(0, "com.a"));
        assertEquals(1, migrated.screenModeFor(0, "com.b"));
    }
    @Test public void malformedPreferencesAndDisabledAppsCannotGrantWake() {
        Map<String, Integer> modes = AppModes.decode(Set.of("bad", "0:com.a=9", "0:com.a=1", "0:com.a=2", "0:com.b=1"));
        assertFalse(modes.containsKey("0:com.a"));
        assertEquals(Integer.valueOf(1), modes.get("0:com.b"));
        assertEquals(0, new WhitelistPolicy(true, Set.of(), 2, modes).screenModeFor(0, "com.b"));
        assertEquals(0, new WhitelistPolicy(false, Set.of("0:com.b"), 2, modes).screenModeFor(0, "com.b"));
    }
    @Test public void snapshotsDoNotFollowMutableUiEdits() {
        Map<String, Integer> modes = new HashMap<>(Map.of("0:com.a", 1));
        WhitelistPolicy p = new WhitelistPolicy(true, Set.of("0:com.a"), 2, modes);
        modes.put("0:com.a", 2);
        assertEquals(1, p.screenModeFor(0, "com.a"));
    }
}
