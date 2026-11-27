package io.github.de0921188.dnd.whitelist;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class NotificationRulesTest {
    private final WhitelistPolicy policy = new WhitelistPolicy(true, Set.of("0:chat","0:mail"),2,
            Map.of("0:chat",0), Set.of("0:chat=Messages=1"));
    @Test public void offAlwaysKeepsNativePolicy() {
        assertFalse(policy.allowsNotification(false,0,"chat","Messages=1"));
        assertFalse(policy.allowsNotification(false,0,"mail",null));
    }
    @Test public void channelIdsMatchExactlyWithinProfile() {
        assertTrue(policy.allowsNotification(true,0,"chat","Messages=1"));
        assertFalse(policy.allowsNotification(true,0,"chat","Messages"));
        assertFalse(policy.allowsNotification(true,10,"chat","Messages=1"));
        assertFalse(policy.allowsNotification(true,0,"chat",null));
        assertTrue(policy.allowsNotification(true,0,"mail",null));
    }
    @Test public void explicitSystemSurvivesRoundTripAndOverridesLegacy() {
        assertEquals(Map.of("0:chat",0),AppModes.decode(AppModes.encode(Map.of("0:chat",0))));
        assertEquals(0,policy.screenModeFor(0,"chat"));
        assertEquals(2,policy.screenModeFor(0,"mail"));
        assertEquals(0,WhitelistPolicy.legacyMode(null));
        assertEquals(1,WhitelistPolicy.legacyMode(1));
    }
    @Test public void darkModeAllowsAudioWithoutLockscreenEffects() {
        String key = "0:chat";
        WhitelistPolicy policy = new WhitelistPolicy(true,Set.of(key),0,Map.of(key,3));
        assertTrue(policy.allowsNotification(true,0,"chat","private"));
        assertFalse(policy.suppressPlayback(true,0,"chat","private",true));
        assertFalse(policy.suppressPlayback(true,0,"chat","private",false));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,
                policy.notificationWakeMode(true,0,"chat","private",false,false,false));
        assertEquals(0,policy.notificationWakeMode(false,0,"chat","private",false,false,false));
        assertEquals(Map.of(key,3),AppModes.decode(AppModes.encode(Map.of(key,3))));
    }
    @Test public void playbackSwitchesAreIndependentAndScoped() {
        String key = "0:chat";
        for (boolean muteSound : new boolean[]{false,true}) {
            for (boolean muteVibration : new boolean[]{false,true}) {
                WhitelistPolicy p = new WhitelistPolicy(true,Set.of(key),1,Map.of(),Set.of(key+"=private"),
                        muteSound ? Set.of(key) : Set.of(), muteVibration ? Set.of(key) : Set.of());
                assertEquals(muteSound,p.suppressPlayback(true,0,"chat","private",true));
                assertEquals(muteVibration,p.suppressPlayback(true,0,"chat","private",false));
                assertFalse(p.suppressPlayback(false,0,"chat","private",true));
                assertFalse(p.suppressPlayback(true,10,"chat","private",false));
                assertFalse(p.suppressPlayback(true,0,"chat","group",true));
                assertEquals(1,p.notificationWakeMode(true,0,"chat","private",false,false,false));
            }
        }
    }
    @Test public void channelFilteredWakeRejectsPrivateAndQuietCompanionUpdates() {
        WhitelistPolicy rules = new WhitelistPolicy(true, Set.of("0:chat"),1,
                Map.of(), Set.of("0:chat=group"));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,rules.notificationWakeMode(true,0,"chat","private",false,false,false));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,rules.notificationWakeMode(true,0,"chat","group",true,false,false));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,rules.notificationWakeMode(true,0,"chat","group",false,true,false));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,rules.notificationWakeMode(true,0,"chat","group",false,false,true));
        WhitelistPolicy edge = new WhitelistPolicy(true, Set.of("0:chat"),2,Map.of(),Set.of("0:chat=group"));
        assertEquals(2,edge.notificationWakeMode(true,0,"chat","group",false,false,false));
        assertEquals(WhitelistPolicy.SUPPRESS_LOCKSCREEN_ALERT,edge.notificationWakeMode(true,0,"chat","private",false,false,false));
        assertEquals(1,rules.notificationWakeMode(true,0,"chat","group",false,false,false));
        assertEquals(0,rules.notificationWakeMode(false,0,"chat","private",false,false,false));
    }
}
