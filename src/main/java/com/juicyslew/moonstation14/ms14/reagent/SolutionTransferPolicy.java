package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Supported projection of SS14 SolutionTransfer; amount is in solution units. */
public record SolutionTransferPolicy(boolean enabled, float transferAmount, boolean canSend, boolean canReceive) {
    public static final Codec<SolutionTransferPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(SolutionTransferPolicy::enabled),
            Codec.FLOAT.optionalFieldOf("transferAmount", 5f).forGetter(SolutionTransferPolicy::transferAmount),
            Codec.BOOL.optionalFieldOf("canSend", true).forGetter(SolutionTransferPolicy::canSend),
            Codec.BOOL.optionalFieldOf("canReceive", true).forGetter(SolutionTransferPolicy::canReceive)
    ).apply(i, SolutionTransferPolicy::new));
    public static final SolutionTransferPolicy DISABLED = new SolutionTransferPolicy(false, 5f, true, true);
    public SolutionTransferPolicy {
        if (!Float.isFinite(transferAmount) || transferAmount <= 0) throw new IllegalArgumentException("invalid transfer amount");
    }
}
