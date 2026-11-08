package com.juicyslew.moonstation14.ms14.botany.processing;

import com.juicyslew.moonstation14.ms14.botany.MachineProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistable machine-owned grind clock. Callers publish completion before doing its work. */
public final class GrinderProgram {
    public enum Operation { GRIND, JUICE }
    private record Saved(long startTick, long endTick, Operation operation) { }
    private static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("start_tick").forGetter(Saved::startTick),
            Codec.LONG.fieldOf("end_tick").forGetter(Saved::endTick),
            Codec.STRING.optionalFieldOf("operation", "GRIND").xmap(Operation::valueOf, Operation::name).forGetter(Saved::operation)
    ).apply(i, Saved::new));
    private long startTick = -1;
    private long endTick = -1;
    private Operation operation = Operation.GRIND;

    public GrinderProgram() { }

    public static Codec<GrinderProgram> codec() { return CODEC.xmap(GrinderProgram::new, GrinderProgram::saved); }
    private GrinderProgram(Saved saved) {
        if (saved.startTick < 0 || saved.endTick <= saved.startTick) throw new IllegalArgumentException("invalid grinder program clock");
        startTick = saved.startTick; endTick = saved.endTick; operation = saved.operation;
    }
    private Saved saved() { if (!active()) throw new IllegalStateException("idle grinder has no saved clock"); return new Saved(startTick, endTick, operation); }
    public boolean active() { return startTick >= 0; }
    public long startTick() { return startTick; }
    public long endTick() { return endTick; }
    public Operation operation() { return operation; }
    public boolean locked() { return active(); }
    public boolean due(long now) { return active() && now >= endTick; }
    public boolean canEject() { return !active(); }

    /** Profile is resolved by the caller from the machine's inherited prototype composition. */
    public boolean start(long now, MachineProfile profile, boolean powered, int inputCount,
                         boolean compatibleBeaker, boolean allInputsHaveNamedGrindSolutions) {
        return start(now, profile, powered, inputCount, compatibleBeaker, allInputsHaveNamedGrindSolutions, Operation.GRIND);
    }
    public boolean start(long now, MachineProfile profile, boolean powered, int inputCount,
                         boolean compatibleBeaker, boolean allInputsEligible, Operation operation) {
        if (active() || now < 0 || !powered || profile == null || profile.extractor()
                || inputCount < 1 || inputCount > profile.chamberCapacity() || !compatibleBeaker
                || !allInputsEligible || operation == null || profile.startDelayTicks() <= 0) return false;
        startTick = now;
        endTick = Math.addExact(now, profile.startDelayTicks());
        this.operation = operation;
        return true;
    }

    /** Clears the active marker exactly once, before the caller invokes its completion callback. */
    public boolean finishIfDue(long now) {
        if (!due(now)) return false;
        startTick = endTick = -1; operation = Operation.GRIND;
        return true;
    }
}
