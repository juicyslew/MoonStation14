package com.juicyslew.moonstation14.ms14.doors.ui.client;

/** Identity-guarded active-window ownership shared by screen removal and bridge callbacks. */
final class AirlockWireLifecycle {
    private Object active;
    void register(Object screen) { active = screen; }
    Object active() { return active; }
    boolean unregister(Object screen) {
        if (active != screen) return false;
        active = null;
        return true;
    }
    boolean isActive(Object screen) { return active == screen; }
}

