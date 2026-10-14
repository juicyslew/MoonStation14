package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Selects presentation health sources without allowing observer hints to replace a canonical body. */
public final class BodyHudSourcePolicy {
    private BodyHudSourcePolicy() { }

    public static <T> T select(T canonicalCharacter, T observerHint, boolean characterControl, boolean ghostControl) {
        if (canonicalCharacter != null) return canonicalCharacter;
        return characterControl || ghostControl ? null : observerHint;
    }
}
