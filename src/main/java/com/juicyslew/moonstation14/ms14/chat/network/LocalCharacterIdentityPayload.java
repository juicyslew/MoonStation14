package com.juicyslew.moonstation14.ms14.chat.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authored saved identity for the committed character's owner, not a speech event. */
public record LocalCharacterIdentityPayload(String name, int rgb, ResourceLocation dimension) implements CustomPacketPayload {
    private static final int MAX_DIMENSION_LENGTH = 256;
    public static final Type<LocalCharacterIdentityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "local_character_identity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LocalCharacterIdentityPayload> STREAM_CODEC =
            StreamCodec.of(LocalCharacterIdentityPayload::write, LocalCharacterIdentityPayload::read);

    public LocalCharacterIdentityPayload {
        // Reuse the ledger's validation, including its color contrast rule.
        new ChatIdentityRegistry.Entry(name, rgb);
        if (dimension == null || dimension.toString().length() > MAX_DIMENSION_LENGTH)
            throw new IllegalArgumentException("Invalid identity dimension");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean inDimension(ResourceLocation currentDimension) {
        return dimension.equals(currentDimension);
    }

    private static void write(RegistryFriendlyByteBuf buf, LocalCharacterIdentityPayload value) {
        buf.writeUtf(value.name, 49);
        buf.writeInt(value.rgb);
        buf.writeUtf(value.dimension.toString(), MAX_DIMENSION_LENGTH);
    }

    private static LocalCharacterIdentityPayload read(RegistryFriendlyByteBuf buf) {
        return new LocalCharacterIdentityPayload(buf.readUtf(49), buf.readInt(),
                ResourceLocation.parse(buf.readUtf(MAX_DIMENSION_LENGTH)));
    }
}
