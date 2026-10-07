package com.juicyslew.moonstation14.ms14.hands.client;

/** Distinguishes a tap on the inventory Alt binding from using Alt as a modifier. */
final class AltTapGesture {
    private boolean armed;
    private boolean usedAsModifier;

    void pressAlt() {
        armed = true;
        usedAsModifier = false;
    }

    void otherPress() {
        if (armed) usedAsModifier = true;
    }

    boolean releaseAlt() {
        boolean toggle = armed && !usedAsModifier;
        armed = false;
        usedAsModifier = false;
        return toggle;
    }

    void clear() {
        armed = false;
        usedAsModifier = false;
    }
}
