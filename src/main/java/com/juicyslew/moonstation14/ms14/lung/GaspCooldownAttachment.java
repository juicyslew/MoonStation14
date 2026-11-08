package com.juicyslew.moonstation14.ms14.lung;

import java.util.OptionalLong;

/** Volatile server-side cooldown shared by all respirator gasp profiles on an entity. */
public final class GaspCooldownAttachment {
    private long lastAttempt = Long.MIN_VALUE;

    /** Read-only snapshot of the last recorded attempt, without changing cooldown state. */
    public OptionalLong lastAttemptTick() {
        return lastAttempt == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(lastAttempt);
    }

    public boolean consume(long gameTime, int cooldownTicks) {
        if (lastAttempt != Long.MIN_VALUE && gameTime >= lastAttempt
                && gameTime - lastAttempt < cooldownTicks) return false;
        lastAttempt = gameTime;
        return true;
    }
}
