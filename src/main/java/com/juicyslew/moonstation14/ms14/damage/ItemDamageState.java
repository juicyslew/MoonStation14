package com.juicyslew.moonstation14.ms14.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.Map;

/** Immutable damage ledger and next permitted high-speed impact tick stored on an item stack. */
public record ItemDamageState(Map<String, Float> damage, long nextImpactDeadline) {
    public static final ItemDamageState EMPTY = new ItemDamageState(Map.of(), 0);
    public static final Codec<ItemDamageState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("damage").forGetter(ItemDamageState::damage),
            Codec.LONG.fieldOf("next_impact_deadline").forGetter(ItemDamageState::nextImpactDeadline)
    ).apply(i, ItemDamageState::new));
    public static final StreamCodec<ByteBuf, ItemDamageState> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public ItemDamageState {
        damage = DamageKeys.validateState(damage);
        if (nextImpactDeadline < 0) throw new IllegalArgumentException("impact deadline must be nonnegative");
        DamageReducer.total(damage);
    }

    public ItemDamageState withDamage(Map<String, Float> delta) {
        return new ItemDamageState(DamageReducer.applyDelta(damage, delta), nextImpactDeadline);
    }
}
