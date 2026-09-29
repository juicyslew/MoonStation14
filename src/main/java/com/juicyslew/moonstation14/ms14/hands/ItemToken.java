package com.juicyslew.moonstation14.ms14.hands;

/** Stable opaque identity for a domain item; deliberately not a Minecraft ItemStack. */
public record ItemToken(String value) {
    public static final int MAX_LENGTH = 128;

    public ItemToken {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Item token must be nonblank and at most " + MAX_LENGTH + " characters");
        }
    }
}
