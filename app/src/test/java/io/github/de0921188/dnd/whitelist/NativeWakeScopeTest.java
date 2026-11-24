package io.github.de0921188.dnd.whitelist;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class NativeWakeScopeTest {
    @Test public void edgeModeSuppressesOnlySelectedNativeWakeAndRestores() {
        NativeWakeScope scope = new NativeWakeScope();
        assertFalse(scope.enter(true, 2));
        assertTrue(scope.enter(false, 0));
        assertFalse(scope.exit());
        assertTrue(scope.exit());
    }
    @Test public void invalidModeCannotGrantWakeAndDisabledWhitelistUsesSystemMode() {
        Set<String> apps = Set.of("0:com.example.chat");
        assertEquals(0, new WhitelistPolicy(true, apps, 99).screenModeFor(0, "com.example.chat"));
        assertEquals(0, new WhitelistPolicy(false, apps, 2).screenModeFor(0, "com.example.chat"));
        assertEquals(2, new WhitelistPolicy(true, apps, 2).screenModeFor(0, "com.example.chat"));
        assertEquals(0, new WhitelistPolicy(true, apps, 2).screenModeFor(10, "com.example.chat"));
    }
    @Test public void unselectedNestedCallbackDoesNotInheritOverride() {
        NativeWakeScope scope = new NativeWakeScope();
        assertTrue(scope.enter(false, true));
        assertFalse(scope.enter(true, false));
        assertTrue(scope.exit());
        assertFalse(scope.exit());
    }
    @Test public void originalEnabledPreferenceSurvivesAndScopeResets() {
        NativeWakeScope scope = new NativeWakeScope();
        assertTrue(scope.enter(true, false));
        assertTrue(scope.exit());
        assertFalse(scope.enter(false, false));
        assertFalse(scope.exit());
    }
    @Test public void bothSwitchesAndExactUserAndPackageAreRequired() {
        Set<String> apps = Set.of("0:com.example.chat");
        assertTrue(new WhitelistPolicy(true, apps, true).allowsWake(0, "com.example.chat"));
        assertFalse(new WhitelistPolicy(false, apps, true).allowsWake(0, "com.example.chat"));
        assertFalse(new WhitelistPolicy(true, apps, false).allowsWake(0, "com.example.chat"));
        assertFalse(new WhitelistPolicy(true, apps, true).allowsWake(999, "com.example.chat"));
        assertFalse(new WhitelistPolicy(true, apps, true).allowsWake(0, "com.other"));
    }
}
