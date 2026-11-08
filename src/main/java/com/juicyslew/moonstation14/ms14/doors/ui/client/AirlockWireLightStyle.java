package com.juicyslew.moonstation14.ms14.doors.ui.client;

/** Explicit two-state red indicator palette; OFF is intentionally unlit and low luminance. */
public final class AirlockWireLightStyle {
    private AirlockWireLightStyle() { }
    public static final int OFF = 0xff4b2020;
    public static final int ON = 0xffff2020;
    public static final int ON_DIAMETER = 8;
    public static final int OFF_DIAMETER = 4;
    /** Physical pixel core size; no opaque white bezel is drawn around either state. */
    public static int diameter(boolean on) { return on ? ON_DIAMETER : OFF_DIAMETER; }
    public static boolean isOn(com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light light,
                               int tick) {
        return light == com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.ON
                || light == com.juicyslew.moonstation14.ms14.doors.ui.AirlockWireIndicators.Light.BLINK_SLOW
                && tick % 20 < 10;
    }
}

