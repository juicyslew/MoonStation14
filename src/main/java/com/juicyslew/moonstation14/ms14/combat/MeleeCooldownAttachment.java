package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Body-owned cooldown used only by the unarmed source. */
public final class MeleeCooldownAttachment {
    public static final com.mojang.serialization.Codec<MeleeCooldownAttachment> CODEC = MeleeCooldownData.CODEC
            .xmap(MeleeCooldownAttachment::new, MeleeCooldownAttachment::data);
    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeCooldownAttachment> STREAM_CODEC =
            MeleeCooldownData.STREAM_CODEC.map(MeleeCooldownAttachment::new, MeleeCooldownAttachment::data);
    private final MeleeCooldownData data;
    public MeleeCooldownAttachment() { this(new MeleeCooldownData(0)); }
    public MeleeCooldownAttachment(MeleeCooldownData data) { this.data = data; }
    public MeleeCooldownData data() { return data; }
}
