package com.juicyslew.moonstation14.ms14.chat.server;

/** Pure interpretation of authenticated raw chat text. This does not authorize or deliver speech. */
public final class SpeechModePolicy {
    public static final int MAX_BODY_LENGTH = 256;
    public static final double WHISPER_CLEAR_RANGE = 3.0;
    public static final double WHISPER_MUFFLED_RANGE = 6.0;
    // Say reaches listeners within 15 blocks.
    public static final double SAY_RANGE = LocalSpeechPolicy.RANGE;
    // Shouting changes presentation only; it must not increase the delivery radius.
    public static final double SHOUT_RANGE = SAY_RANGE;

    private SpeechModePolicy() { }

    public enum Mode { SAY, WHISPER, SHOUT, RADIO_ATTEMPT, INVALID }

    /** channelKey is non-null only for a single-letter addressed radio attempt. */
    public record Result(Mode mode, String body, Character channelKey) { }

    private static final Result INVALID = new Result(Mode.INVALID, null, null);

    /** Removes only explicit leading mode markers. No trimming or formatting normalization occurs. */
    public static Result parse(String rawText) {
        if (rawText == null) return INVALID;
        String text = rawText;
        boolean forceLocal = text.startsWith(">");
        if (forceLocal) text = text.substring(1);

        Mode mode;
        Character channel = null;
        if (text.startsWith(",")) {
            mode = Mode.WHISPER;
            text = text.substring(1);
        } else if (!forceLocal && text.startsWith(";")) {
            mode = Mode.RADIO_ATTEMPT;
            text = text.substring(1);
        } else if (!forceLocal && text.startsWith(":") && text.length() > 1
                && !Character.isWhitespace(text.charAt(1))) {
            // A colon followed by a token is an attempted channel, never fallback local speech.
            if (text.charAt(1) < 'a' || text.charAt(1) > 'z'
                    || (text.length() > 2 && Character.isLetterOrDigit(text.charAt(2)))) return INVALID;
            mode = Mode.RADIO_ATTEMPT;
            channel = text.charAt(1);
            text = text.substring(2);
        } else {
            mode = text.endsWith("!!") ? Mode.SHOUT : Mode.SAY;
        }

        if (text.isBlank() || text.length() > MAX_BODY_LENGTH) return INVALID;
        return new Result(mode, text, channel);
    }
}
