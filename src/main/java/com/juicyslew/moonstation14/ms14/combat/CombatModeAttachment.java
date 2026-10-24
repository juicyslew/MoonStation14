package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Body-owned runtime combat mode state. */
public final class CombatModeAttachment {
    private boolean inCombatMode;

    public CombatModeAttachment() { this(false); }
    public CombatModeAttachment(boolean inCombatMode) { this.inCombatMode = inCombatMode; }
    public boolean inCombatMode() { return inCombatMode; }
    public void set(boolean value) { inCombatMode = value; }

    public static final Codec<CombatModeAttachment> CODEC = Codec.BOOL.xmap(CombatModeAttachment::new, CombatModeAttachment::inCombatMode);
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatModeAttachment> STREAM_CODEC = StreamCodec.of(
            (buffer, attachment) -> ByteBufCodecs.BOOL.encode(buffer, attachment.inCombatMode()),
            buffer -> new CombatModeAttachment(ByteBufCodecs.BOOL.decode(buffer)));
}
