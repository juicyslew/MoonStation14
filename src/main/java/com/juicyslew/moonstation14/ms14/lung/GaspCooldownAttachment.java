package com.juicyslew.moonstation14.ms14.lung;

/** Volatile server-side cooldown shared by all respirator gasp profiles on an entity. */
public final class GaspCooldownAttachment {
    private long lastAttempt = Long.MIN_VALUE;

    public boolean consume(long gameTime, int cooldownTicks) {
        if (lastAttempt != Long.MIN_VALUE && gameTime >= lastAttempt
                && gameTime - lastAttempt < cooldownTicks) return false;
        lastAttempt = gameTime;
        return true;
    }
}
