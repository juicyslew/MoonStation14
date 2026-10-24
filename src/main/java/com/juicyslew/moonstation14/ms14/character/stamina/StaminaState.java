package com.juicyslew.moonstation14.ms14.character.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable server-authoritative runtime value; no prototype or entity attachment is implied. */
public record StaminaState(float damage, boolean critical, boolean afterCritical, long nextUpdateTick) {
    public static final StaminaState EMPTY = new StaminaState(0, false, false, 0);
    private static final Codec<Float> DAMAGE_CODEC = Codec.FLOAT.validate(value ->
            Float.isFinite(value) && value >= 0 ? DataResult.success(value)
                    : DataResult.error(() -> "stamina damage must be finite and nonnegative"));
    private static final Codec<Long> TICK_CODEC = Codec.LONG.validate(value -> value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "stamina tick must be nonnegative"));
    public static final Codec<StaminaState> CODEC = RecordCodecBuilder.create(i -> i.group(
            DAMAGE_CODEC.fieldOf("damage").forGetter(StaminaState::damage),
            Codec.BOOL.fieldOf("critical").forGetter(StaminaState::critical),
            Codec.BOOL.fieldOf("after_critical").forGetter(StaminaState::afterCritical),
            TICK_CODEC.fieldOf("next_update_tick").forGetter(StaminaState::nextUpdateTick)
    ).apply(i, StaminaState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaminaState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaState::damage, ByteBufCodecs.BOOL, StaminaState::critical,
            ByteBufCodecs.BOOL, StaminaState::afterCritical, ByteBufCodecs.VAR_LONG, StaminaState::nextUpdateTick,
            StaminaState::new);
    public StaminaState {
        if (!Float.isFinite(damage) || damage < 0 || nextUpdateTick < 0) throw new IllegalArgumentException("invalid stamina state");
    }
}
