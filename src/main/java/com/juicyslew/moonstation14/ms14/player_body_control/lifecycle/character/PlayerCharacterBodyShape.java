package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

/** Independent bounded wide/slim body model choice for the character harness. */
public enum PlayerCharacterBodyShape {
    WIDE(0),
    SLIM(1);

    private final int index;

    PlayerCharacterBodyShape(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    /** Returns null for unsupported persisted or synchronized values. */
    public static PlayerCharacterBodyShape fromIndex(int index) {
        for (PlayerCharacterBodyShape shape : values()) {
            if (shape.index == index) return shape;
        }
        return null;
    }
}
