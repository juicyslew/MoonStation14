package com.juicyslew.moonstation14.ms14.equipment.client;

/** UI-only collapse state. Call beginBody when the controlled body epoch changes. */
public final class EquipmentPanelState {
    private long bodyEpoch = Long.MIN_VALUE;
    private boolean expanded = true;
    public boolean expanded(long epoch) { beginBody(epoch); return expanded; }
    public void toggle(long epoch) { beginBody(epoch); expanded = !expanded; }
    public void beginBody(long epoch) { if (bodyEpoch != epoch) { bodyEpoch = epoch; expanded = true; } }
}

