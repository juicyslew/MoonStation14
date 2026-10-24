package com.juicyslew.moonstation14.ms14.chat.server;

import com.juicyslew.moonstation14.ms14.chat.server.SpeechRecipientPolicy.Delivery;

/** Small policy seam for locally heard speech and autonomous phrase replay. */
public final class ParrotMemoryPolicy {
    private ParrotMemoryPolicy() { }

    public static boolean canLearn(boolean listenerCapability, boolean memoryCapability, boolean alive,
                                   boolean autonomous, boolean contained, boolean self, Delivery delivery,
                                   double distanceSquared, double hearingRange, String message,
                                   int minimumLength, int maximumLength, long now, long nextLearnAt) {
        if (!listenerCapability || !memoryCapability || !alive || !autonomous || contained || self
                || delivery != Delivery.CLEAR || !Double.isFinite(distanceSquared) || distanceSquared < 0
                || !Double.isFinite(hearingRange) || hearingRange <= 0
                || distanceSquared > hearingRange * hearingRange || message == null) return false;
        String text = message.trim();
        return !text.isEmpty() && !text.contains("~") && text.length() >= minimumLength
                && text.length() <= maximumLength && now >= nextLearnAt;
    }

    public static boolean shouldReplay(boolean memoryCapability, boolean alive, boolean autonomous,
                                       boolean contained, int entries, long now, long nextReplayAt) {
        return memoryCapability && alive && autonomous && !contained && entries > 0 && now >= nextReplayAt;
    }

    public static int replacementIndex(int currentSize, int capacity, int randomIndex) {
        if (currentSize < 0 || capacity < 1 || randomIndex < 0) throw new IllegalArgumentException("invalid memory bounds");
        if (currentSize < capacity) return -1;
        if (currentSize == 0 || randomIndex >= currentSize) throw new IllegalArgumentException("invalid replacement index");
        return randomIndex;
    }

    public static long nextInterval(long now, long minimum, long maximum, int randomOffset) {
        if (now < 0 || minimum < 1 || maximum < minimum || randomOffset < 0 || randomOffset > maximum - minimum)
            throw new IllegalArgumentException("invalid replay interval");
        return Math.addExact(now, minimum + randomOffset);
    }
}
