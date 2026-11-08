package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Absolute next eligible attack tick, persisted on the source weapon (or unarmed body). */
public record MeleeCooldownData(long nextAttackTick, boolean swingLeft) {
    public MeleeCooldownData(long nextAttackTick) { this(nextAttackTick, false); }
    public MeleeCooldownData withDeadline(long deadline) { return new MeleeCooldownData(deadline, swingLeft); }
    public static final Codec<MeleeCooldownData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("next_attack_tick").forGetter(MeleeCooldownData::nextAttackTick),
            Codec.BOOL.optionalFieldOf("swing_left", false).forGetter(MeleeCooldownData::swingLeft)
    ).apply(i, MeleeCooldownData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeCooldownData> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { ByteBufCodecs.VAR_LONG.encode(buffer, value.nextAttackTick()); ByteBufCodecs.BOOL.encode(buffer, value.swingLeft()); },
            buffer -> new MeleeCooldownData(ByteBufCodecs.VAR_LONG.decode(buffer), ByteBufCodecs.BOOL.decode(buffer)));

    /** Minecraft adaptation: one second is 20 ticks; reject invalid rates and overflowing deadlines. */
    public static java.util.OptionalLong deadline(long nowTick, float attacksPerSecond) {
        if (!Float.isFinite(attacksPerSecond) || attacksPerSecond <= 0f) return java.util.OptionalLong.empty();
        double ticks = Math.ceil(20.0d / attacksPerSecond);
        if (!Double.isFinite(ticks) || ticks < 1d || ticks > Long.MAX_VALUE) return java.util.OptionalLong.empty();
        long duration = (long) ticks;
        if (nowTick > Long.MAX_VALUE - duration) return java.util.OptionalLong.empty();
        return java.util.OptionalLong.of(nowTick + duration);
    }
}
