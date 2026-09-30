package com.juicyslew.moonstation14.ms14.chat.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.server.LocalSpeechPolicy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Unsigned, server-authored local speech. Contains no account identifier or signed-chat metadata. */
public record LocalSpeechPayload(int speakerEntityId, UUID speakerUuid, String actualName, int rgb, String text,
                                   double x, double y, double z, ResourceLocation dimension,
                                   Mode mode, int azimuthSector, int verticalBand, DistanceTier distanceTier) implements CustomPacketPayload {
    private static final int MAX_DIMENSION_LENGTH = 256;
    public static final int NO_BEARING = -1;
    public static final UUID NO_UUID = new UUID(0, 0);
    public static final int HORIZONTAL_SECTORS = 16;
    public static final int BAND_LOW = 0;
    public static final int BAND_LEVEL = 1;
    public static final int BAND_HIGH = 2;
    /** Vertical-only bearings have no horizontal direction; sector 0 is a placeholder. */
    public static final int BAND_UP = 3;
    public static final int BAND_DOWN = 4;
    public static final String ANONYMOUS_NAME = "Someone";
    public static final int ANONYMOUS_RGB = 0xFFFFFF;
    public enum Mode { SAY, WHISPER, SHOUT, W_MUFFLED }
    public enum DistanceTier { NONE, NEAR, FAR }
    public static final Type<LocalSpeechPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "local_speech"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LocalSpeechPayload> STREAM_CODEC =
            StreamCodec.of(LocalSpeechPayload::write, LocalSpeechPayload::read);

    public LocalSpeechPayload {
        if (mode == null || speakerUuid == null || distanceTier == null || (mode == Mode.W_MUFFLED
                ? speakerEntityId <= 0 || NO_UUID.equals(speakerUuid) || !validName(actualName) || rgb != ANONYMOUS_RGB
                    || Double.doubleToLongBits(x) != Double.doubleToLongBits(0.0)
                    || Double.doubleToLongBits(y) != Double.doubleToLongBits(0.0)
                    || Double.doubleToLongBits(z) != Double.doubleToLongBits(0.0)
                    || azimuthSector < 0 || azimuthSector >= HORIZONTAL_SECTORS
                    || verticalBand < BAND_LOW || verticalBand > BAND_DOWN
                    || ((verticalBand == BAND_UP || verticalBand == BAND_DOWN) && azimuthSector != 0)
                    || distanceTier == DistanceTier.NONE
                : speakerEntityId <= 0 || !validName(actualName)
                    || !ChatIdentityRegistry.validColor(rgb)
                    || azimuthSector != NO_BEARING || verticalBand != NO_BEARING
                    || distanceTier != DistanceTier.NONE)
                || !LocalSpeechPolicy.validText(text)
                  || !LocalSpeechPolicy.validPosition(x, y, z)
                  || dimension == null || dimension.toString().length() > MAX_DIMENSION_LENGTH)
            throw new IllegalArgumentException("Invalid local speech payload");
    }

    /** Legacy clear packet API for local fixtures; server-authored speech uses the canonical UUID constructor. */
    public LocalSpeechPayload(int speakerEntityId, String actualName, int rgb, String text,
                               double x, double y, double z, ResourceLocation dimension, Mode mode) {
        this(speakerEntityId, NO_UUID, actualName, rgb, text, x, y, z, dimension, mode,
                NO_BEARING, NO_BEARING, DistanceTier.NONE);
    }

    /** Legacy bearing signature remains clear-only; it cannot construct an anonymous muffled packet. */
    public LocalSpeechPayload(int speakerEntityId, String actualName, int rgb, String text,
                              double x, double y, double z, ResourceLocation dimension, Mode mode,
                              int azimuthSector, int verticalBand) {
        this(speakerEntityId, NO_UUID, actualName, rgb, text, x, y, z, dimension, mode,
                azimuthSector, verticalBand, DistanceTier.NONE);
    }

    /** Compatibility for existing clear-say call sites. */
    public LocalSpeechPayload(int speakerEntityId, String actualName, int rgb, String text,
                              double x, double y, double z, ResourceLocation dimension) {
        this(speakerEntityId, actualName, rgb, text, x, y, z, dimension, Mode.SAY);
    }

    private static boolean validName(String name) {
        return name != null && (name.matches("[A-Za-z]{2,24} [A-Za-z]{2,24}")
                || name.matches("[A-Z][A-Za-z]{1,31} \\((?:[1-9][0-9]{0,5}|1000000)\\)"));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public boolean inDimension(ResourceLocation currentDimension) {
        return dimension.equals(currentDimension);
    }

    private static void write(RegistryFriendlyByteBuf buf, LocalSpeechPayload value) {
        buf.writeVarInt(value.speakerEntityId);
        buf.writeUUID(value.speakerUuid);
        buf.writeUtf(value.actualName, 49);
        buf.writeInt(value.rgb);
        buf.writeUtf(value.text, LocalSpeechPolicy.MAX_TEXT_LENGTH);
        buf.writeDouble(value.x);
        buf.writeDouble(value.y);
        buf.writeDouble(value.z);
        buf.writeUtf(value.dimension.toString(), MAX_DIMENSION_LENGTH);
        buf.writeEnum(value.mode);
        buf.writeByte(value.azimuthSector);
        buf.writeByte(value.verticalBand);
        buf.writeEnum(value.distanceTier);
    }

    private static LocalSpeechPayload read(RegistryFriendlyByteBuf buf) {
        return new LocalSpeechPayload(buf.readVarInt(), buf.readUUID(), buf.readUtf(49), buf.readInt(),
                buf.readUtf(LocalSpeechPolicy.MAX_TEXT_LENGTH), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                ResourceLocation.parse(buf.readUtf(MAX_DIMENSION_LENGTH)), buf.readEnum(Mode.class),
                buf.readByte(), buf.readByte(), buf.readEnum(DistanceTier.class));
    }
}
