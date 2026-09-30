package com.juicyslew.moonstation14.ms14.chat.client;

/** Only the accepted, non-action-bar system packet path may enter the visual notice feed. */
public final class LocalChatVisualPolicy {
    public enum Source { ACCEPTED_SYSTEM_CHAT, GENERIC_CHAT_INSERTION, PLAYER_CHAT, DISGUISED_CHAT }

    private LocalChatVisualPolicy() { }

    public static boolean shouldMirrorNotice(Source source, boolean actionBar, boolean inWorld) {
        return inWorld && !actionBar && source == Source.ACCEPTED_SYSTEM_CHAT;
    }

    public static boolean shouldHideVanillaHistory(boolean featureActive, boolean inWorld) {
        return featureActive && inWorld;
    }

    /** Match vanilla's signed scroll amount without letting large wheel deltas skip U pages. */
    public static int scrollDirection(int amount) {
        return Integer.signum(amount);
    }
}
