package com.juicyslew.moonstation14.ms14.eye;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable, validated authoritative eye-damage state for one character. */
public record EyeDamageComponent(int damage)
        implements IMS14Component<EyeDamageComponent, EyeDamageAttachment> {
    public static final int MIN_DAMAGE = 0;
    public static final int MAX_DAMAGE = 9;
    public static final EyeDamageComponent EMPTY = new EyeDamageComponent(MIN_DAMAGE);

    public EyeDamageComponent {
        if (damage < MIN_DAMAGE || damage > MAX_DAMAGE) {
            throw new IllegalArgumentException("eye damage must be in [0, 9]");
        }
    }

    public EyeDamageComponent() {
        this(MIN_DAMAGE);
    }

    public boolean isEmpty() {
        return damage == MIN_DAMAGE;
    }

    public boolean isBlind() {
        return damage >= MAX_DAMAGE;
    }

    @Override
    public EyeDamageAttachment toAttachment() {
        return new EyeDamageAttachment(this);
    }

    private static final Codec<Integer> DAMAGE_CODEC = Codec.INT.comapFlatMap(
            value -> value >= MIN_DAMAGE && value <= MAX_DAMAGE
                    ? DataResult.success(value)
                    : DataResult.error(() -> "eye damage must be in [0, 9]"),
            value -> value);

    public static final Codec<EyeDamageComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DAMAGE_CODEC.fieldOf("damage").forGetter(EyeDamageComponent::damage)
    ).apply(instance, EyeDamageComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EyeDamageComponent> STREAM_CODEC =
            StreamCodec.of((buffer, value) -> buffer.writeVarInt(value.damage()),
                    buffer -> new EyeDamageComponent(buffer.readVarInt()));

    @Override
    public Codec<EyeDamageComponent> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, EyeDamageComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
