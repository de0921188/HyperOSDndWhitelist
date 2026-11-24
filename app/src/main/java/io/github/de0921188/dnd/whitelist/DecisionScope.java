package io.github.de0921188.dnd.whitelist;

/** Nested decisions must restore the parent state, including when a decision throws. */
final class DecisionScope {
    private final ThreadLocal<Boolean> selected = ThreadLocal.withInitial(() -> false);
    boolean active() { return selected.get(); }
    boolean enter(boolean allow) {
        boolean previous = selected.get();
        selected.set(allow);
        return previous;
    }
    void restore(boolean previous) {
        if (previous) selected.set(true); else selected.remove();
    }
}
