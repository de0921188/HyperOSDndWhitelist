package io.github.de0921188.dnd.whitelist;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class HookRunnerTest {
    @Test public void earlyResultSkipsOriginal() throws Throwable {
        AtomicInteger calls = new AtomicInteger();
        Object result = HookRunner.run(null, new Object[0], () -> { calls.incrementAndGet(); return true; },
                new HookRunner.Callback() {
                    @Override protected void beforeHookedMethod(HookRunner.Invocation call) { call.setResult(false); }
                }, error -> fail(error.toString()));
        assertEquals(false, result); assertEquals(0, calls.get());
    }
    @Test public void originalExceptionPropagatesAfterScopeRestoration() {
        DecisionScope scope = new DecisionScope();
        IllegalStateException expected = new IllegalStateException("original");
        Throwable actual = assertThrows(IllegalStateException.class, () -> HookRunner.run(null, new Object[0], () -> {
            assertTrue(scope.active()); throw expected;
        }, new HookRunner.Callback() {
            @Override protected void beforeHookedMethod(HookRunner.Invocation call) { call.setObjectExtra("previous", scope.enter(true)); }
            @Override protected void afterHookedMethod(HookRunner.Invocation call) { scope.restore((Boolean) call.getObjectExtra("previous")); }
        }, error -> fail(error.toString())));
        assertSame(expected, actual); assertFalse(scope.active());
    }
    @Test public void callbackFailuresDoNotRetryOrLoseOriginalResult() throws Throwable {
        AtomicInteger calls = new AtomicInteger(), failures = new AtomicInteger();
        Object value = HookRunner.run(null, new Object[0], () -> { calls.incrementAndGet(); return 42; },
                new HookRunner.Callback() {
                    @Override protected void beforeHookedMethod(HookRunner.Invocation call) { throw new IllegalArgumentException(); }
                    @Override protected void afterHookedMethod(HookRunner.Invocation call) { call.setResult(99); throw new IllegalArgumentException(); }
                }, error -> failures.incrementAndGet());
        assertEquals(42, value); assertEquals(1, calls.get()); assertEquals(2, failures.get());
    }
    @Test public void afterHookSeesArgumentsAndCanModifyResult() throws Throwable {
        Object target = new Object();
        Object result = HookRunner.run(target, new Object[]{"a"}, () -> "old", new HookRunner.Callback() {
            @Override protected void afterHookedMethod(HookRunner.Invocation call) {
                assertSame(target, call.thisObject); assertEquals("a", call.args[0]);
                assertFalse(call.hasThrowable()); call.setResult("new");
            }
        }, error -> fail(error.toString()));
        assertEquals("new", result);
    }
}
