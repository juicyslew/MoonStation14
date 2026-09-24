package com.juicyslew.moonstation14.ms14.alert;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.OptionalLong;

/** Alert runtime state; deadline is an absolute server game tick, absent for persistent alerts. */
public record AlertInstance(OptionalLong deadline, boolean showCooldown) {
    private static final Codec<Long> POSITIVE_DEADLINE = Codec.LONG.validate(value -> value > 0
            ? DataResult.success(value) : DataResult.error(() -> "alert deadline must be positive"));
    public static final Codec<AlertInstance> CODEC = RecordCodecBuilder.<AlertInstance>create(instance -> instance.group(
            POSITIVE_DEADLINE.optionalFieldOf("deadline").forGetter((AlertInstance value) -> value.deadline().isPresent()
                    ? java.util.Optional.of(value.deadline().getAsLong()) : java.util.Optional.empty()),
            Codec.BOOL.fieldOf("show_cooldown").forGetter(AlertInstance::showCooldown)
    ).apply(instance, (deadline, cooldown) -> new AlertInstance(
            deadline.isPresent() ? OptionalLong.of(deadline.get()) : OptionalLong.empty(), cooldown)))
            .validate((AlertInstance value) -> value.deadline().isEmpty() || value.deadline().getAsLong() > 0
                    ? DataResult.success(value) : DataResult.error(() -> "alert deadline must be positive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlertInstance> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, value -> value.deadline().orElse(0L),
            ByteBufCodecs.BOOL, AlertInstance::showCooldown,
            (deadline, cooldown) -> {
                if (deadline < 0) throw new IllegalArgumentException("alert deadline must be nonnegative");
                return new AlertInstance(deadline == 0 ? OptionalLong.empty() : OptionalLong.of(deadline), cooldown);
            });

    public AlertInstance {
        if (deadline == null) throw new NullPointerException("deadline");
        if (deadline.isPresent() && deadline.getAsLong() <= 0)
            throw new IllegalArgumentException("alert deadline must be positive");
    }
}
