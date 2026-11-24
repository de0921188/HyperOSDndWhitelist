package com.qing.dnd.whitelist;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class DecisionScopeTest {
    @Test public void nestedUnselectedDecisionCannotInheritBypass() {
        DecisionScope scope = new DecisionScope();
        boolean outer = scope.enter(true);
        assertTrue(scope.active());
        boolean inner = scope.enter(false);
        assertFalse(scope.active());
        scope.restore(inner);
        assertTrue(scope.active());
        scope.restore(outer);
        assertFalse(scope.active());
    }
    @Test public void exceptionsRestoreAndOtherThreadsRemainUnaffected() throws InterruptedException {
        DecisionScope scope = new DecisionScope();
        boolean previous = scope.enter(true);
        AtomicBoolean other = new AtomicBoolean(true);
        Thread thread = new Thread(() -> other.set(scope.active()));
        thread.start();
        thread.join();
        assertFalse(other.get());
        try { throw new IllegalStateException("test"); }
        catch (IllegalStateException expected) { assertTrue(scope.active()); }
        finally { scope.restore(previous); }
        assertFalse(scope.active());
    }
}
