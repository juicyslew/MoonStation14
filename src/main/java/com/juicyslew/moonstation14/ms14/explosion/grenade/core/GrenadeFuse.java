package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

/** Immutable timing-only state for a one-shot player grenade. Game time is persisted as an absolute tick. */
public final class GrenadeFuse {
    public static final long FUSE_TICKS = 60;
    public static final long SECOND_BEEP_TICK = 40;

    public enum Phase { SAFE, ARMED, SPENT }

    private final Phase phase;
    private final long deadline;
    private final boolean beepedAtPrime;
    private final boolean beepedAtForty;

    private GrenadeFuse(Phase phase, long deadline, boolean beepedAtPrime, boolean beepedAtForty) {
        this.phase = phase;
        this.deadline = deadline;
        this.beepedAtPrime = beepedAtPrime;
        this.beepedAtForty = beepedAtForty;
    }

    public static GrenadeFuse safe() { return new GrenadeFuse(Phase.SAFE, -1, false, false); }

    public static GrenadeFuse restore(Phase phase, long deadline, boolean beepedAtPrime, boolean beepedAtForty) {
        if (phase == null) throw new IllegalArgumentException("phase is required");
        if (phase == Phase.SAFE && (deadline != -1 || beepedAtPrime || beepedAtForty))
            throw new IllegalArgumentException("invalid safe state");
        if (phase == Phase.ARMED && (deadline < FUSE_TICKS || (beepedAtForty && !beepedAtPrime)))
            throw new IllegalArgumentException("invalid armed state");
        if (phase == Phase.SPENT && deadline < FUSE_TICKS)
            throw new IllegalArgumentException("invalid spent state");
        return new GrenadeFuse(phase, deadline, beepedAtPrime, beepedAtForty);
    }

    public GrenadeFuse prime(long now) {
        requireTime(now);
        if (phase != Phase.SAFE) throw new IllegalStateException("grenade is not safe");
        if (now > Long.MAX_VALUE - FUSE_TICKS) throw new IllegalArgumentException("deadline overflow");
        return new GrenadeFuse(Phase.ARMED, now + FUSE_TICKS, false, false);
    }

    /** Evaluates due milestones once. Expiry suppresses missed beeps rather than replaying a backlog. */
    public Evaluation evaluate(long now) {
        requireTime(now);
        if (phase != Phase.ARMED) return new Evaluation(this, false, false);
        long primeTick = deadline - FUSE_TICKS;
        boolean expire = now >= deadline;
        boolean beep = !expire && !beepedAtPrime && now >= primeTick;
        boolean second = !expire && !beepedAtForty && now >= primeTick + SECOND_BEEP_TICK;
        GrenadeFuse next = new GrenadeFuse(expire ? Phase.SPENT : Phase.ARMED, deadline,
                beepedAtPrime || beep, beepedAtForty || second);
        return new Evaluation(next, beep || second, expire);
    }

    public Phase phase() { return phase; }
    public boolean beepedAtPrime() { return beepedAtPrime; }
    public boolean beepedAtForty() { return beepedAtForty; }
    public long deadline() { if (phase == Phase.SAFE) throw new IllegalStateException("safe grenade has no deadline"); return deadline; }
    public long remaining(long now) {
        requireTime(now);
        return phase == Phase.ARMED ? Math.max(0, deadline - now) : 0;
    }

    private static void requireTime(long time) {
        if (time < 0) throw new IllegalArgumentException("game time must be nonnegative");
    }

    public static final class Evaluation {
        private final GrenadeFuse state;
        private final boolean beepDue;
        private final boolean expired;
        private Evaluation(GrenadeFuse state, boolean beepDue, boolean expired) {
            this.state = state; this.beepDue = beepDue; this.expired = expired;
        }
        public GrenadeFuse state() { return state; }
        public boolean beepDue() { return beepDue; }
        public boolean expired() { return expired; }
    }
}

