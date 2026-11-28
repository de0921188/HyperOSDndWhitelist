package io.github.de0921188.dnd.whitelist;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelOptionsTest {
    private static final String KEY="0:test.app";
    private WhitelistPolicy policy(Set<String> rules,Set<String> options) {
        return new WhitelistPolicy(true,Set.of(KEY),1,Map.of(),rules,Set.of(KEY),Set.of(),options);
    }
    @Test public void oldEmptyAllowsAllExplicitEmptyAllowsNone() {
        assertTrue(policy(Set.of(),Set.of()).allowsNotification(true,0,"test.app","first"));
        assertFalse(policy(Set.of(),Set.of(KEY+"=selected")).allowsNotification(true,0,"test.app","first"));
    }
    @Test public void inheritsParentAndOverridesOneEffect() {
        String parent="a=:b", child="c=d";
        WhitelistPolicy p=policy(Set.of(KEY+"="+parent),Set.of(
            ChannelOptions.prefix(KEY,"parent",child)+ChannelOptions.encode(parent),
            ChannelOptions.prefix(KEY,"sound",parent)+"1",ChannelOptions.prefix(KEY,"mode",parent)+"2",
            ChannelOptions.prefix(KEY,"vibration",child)+"0"));
        assertTrue(p.allowsNotification(true,0,"test.app",child));
        assertFalse(p.suppressPlayback(true,0,"test.app",child,true));
        assertTrue(p.suppressPlayback(true,0,"test.app",child,false));
        assertEquals(2,p.notificationWakeMode(true,0,"test.app",child,false,false,false));
        assertEquals(-1,p.notificationWakeMode(true,0,"test.app",child,false,true,false));
        assertFalse(p.allowsNotification(true,1,"test.app",child));
    }
}
