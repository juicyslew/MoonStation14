package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

/** Bounded first appearance slice; every option uses the vanilla wide player model. */
public enum PlayerCharacterAppearance {
    DEFAULT(0),
    ALEX(1);

    private final int index;

    PlayerCharacterAppearance(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    /** Returns null for unsupported persisted or synchronized values. */
    public static PlayerCharacterAppearance fromIndex(int index) {
        for (PlayerCharacterAppearance appearance : values()) {
            if (appearance.index == index) return appearance;
        }
        return null;
    }
}
