package com.juicyslew.moonstation14.ms14.mobs;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Mutable attachment facade; gameplay mutation is performed by the server system. */
public final class MobStateAttachment {
    private MobStateData data;
    public MobStateAttachment() { data = MobStateData.DEFAULT; }
    public MobStateAttachment(MobStateData data) { this.data = java.util.Objects.requireNonNull(data); }
    public MobStateData data() { return data; }
    public void set(MobStateData value) { data = java.util.Objects.requireNonNull(value); }
    public static final Codec<MobStateAttachment> CODEC = MobStateData.CODEC.xmap(MobStateAttachment::new, MobStateAttachment::data);
    public static final StreamCodec<RegistryFriendlyByteBuf, MobStateAttachment> STREAM_CODEC =
            StreamCodec.of((RegistryFriendlyByteBuf buf, MobStateAttachment value) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.data.state().name());
                ByteBufCodecs.VAR_LONG.encode(buf, value.data.revision());
            }, buf -> new MobStateAttachment(new MobStateData(
                    com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.valueOf(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    ByteBufCodecs.VAR_LONG.decode(buf))));
}

