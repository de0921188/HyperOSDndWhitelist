package io.github.de0921188.dnd.whitelist;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class WhitelistPolicyTest {
    @Test public void dndExternalSilenceUsesInternalModeOnlyForSelectedDecision() {
        assertEquals(2, WhitelistPolicy.vibrationRingerMode(true, 0, 2));
        assertEquals(1, WhitelistPolicy.vibrationRingerMode(true, 0, 1));
        assertEquals(0, WhitelistPolicy.vibrationRingerMode(false, 0, 2));
    }
    @Test public void explicitSilentAndNormalExternalModesArePreserved() {
        assertEquals(0, WhitelistPolicy.vibrationRingerMode(true, 0, 0));
        assertEquals(2, WhitelistPolicy.vibrationRingerMode(true, 2, 0));
        assertEquals(1, WhitelistPolicy.vibrationRingerMode(true, 1, 2));
        assertEquals(0, WhitelistPolicy.vibrationRingerMode(true, 0, -1));
    }
    @Test public void disabledNeverBypasses() {
        assertFalse(new WhitelistPolicy(false, Set.of("0:com.test.app")).allows(0, "com.test.app"));
    }
    @Test public void onlyExactPackageAndProfileMatch() {
        WhitelistPolicy p = new WhitelistPolicy(true, Set.of("0:com.test.app"));
        assertTrue(p.allows(0, "com.test.app"));
        assertFalse(p.allows(999, "com.test.app"));
        assertFalse(p.allows(10, "com.test.app"));
        assertFalse(p.allows(-1, "com.test.app"));
        assertFalse(p.allows(0, "com.test.app.evil"));
        assertFalse(p.allows(0, null));
    }
    @Test public void visualExemptionRetainsFullScreenAndUnknownBits() {
        WhitelistPolicy p = new WhitelistPolicy(true, Set.of("0:com.test.app"));
        int effects = WhitelistPolicy.VISUAL_MASK | 4 | 1024;
        assertEquals(4 | 1024, p.visualEffects(0, "com.test.app", effects));
        assertEquals(effects, p.visualEffects(0, "com.other", effects));
    }
    @Test public void snapshotDoesNotFollowMutableEditorSet() {
        Set<String> input = new HashSet<>(Set.of("0:com.test.app"));
        WhitelistPolicy p = new WhitelistPolicy(true, input);
        input.clear();
        assertTrue(p.allows(0, "com.test.app"));
        assertFalse(new WhitelistPolicy(true, input).allows(0, "com.test.app"));
    }
}
