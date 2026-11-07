package com.juicyslew.moonstation14.gametest.support;

/** Keeps GameTest failure text within the limits of native failure artifacts. */
public final class GameTestFailureMessage {
    public static final int MAX_LENGTH = 240;

    private GameTestFailureMessage() { }

    public static String concise(String message) {
        return message.length() <= MAX_LENGTH ? message : message.substring(0, MAX_LENGTH - 3) + "...";
    }
}
