package com.qing.dnd.whitelist;

import java.util.ArrayDeque;

/** Restores the native preference after each synchronous main-thread notification callback. */
final class NativeWakeScope {
    private final ThreadLocal<ArrayDeque<Boolean>> frames = ThreadLocal.withInitial(ArrayDeque::new);
    boolean enter(boolean current, boolean selected) {
        return enter(current, selected ? 1 : 0);
    }
    boolean enter(boolean current, int mode) {
        ArrayDeque<Boolean> stack = frames.get();
        boolean baseline = stack.isEmpty() ? current : stack.peekLast();
        stack.push(current);
        return mode == 1 || (mode != 2 && baseline);
    }
    boolean exit() {
        ArrayDeque<Boolean> stack = frames.get();
        boolean previous = stack.pop();
        if (stack.isEmpty()) frames.remove();
        return previous;
    }
}
