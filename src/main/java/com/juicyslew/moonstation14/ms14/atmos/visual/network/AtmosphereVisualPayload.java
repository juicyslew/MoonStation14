package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Server-authored visual-only atmosphere state. This schema is not registered or handled here. */
public record AtmosphereVisualPayload(ResourceLocation dimension, int chunkX, int chunkZ, long revision,
             boolean resetSnapshot, boolean finalPacket, List<VisualCell> cells)
        implements CustomPacketPayload {
    public static final Type<AtmosphereVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "atmosphere_visual"));
    public static final int MAX_CELLS = 256;
    public static final int MIN_BUILD_Y = -2048;
    public static final int MAX_BUILD_Y = 2047;

    public static final StreamCodec<RegistryFriendlyByteBuf, AtmosphereVisualPayload> STREAM_CODEC =
            StreamCodec.of(AtmosphereVisualPayload::write, AtmosphereVisualPayload::read);

    public AtmosphereVisualPayload {
        Objects.requireNonNull(dimension, "dimension");
        if (revision <= 0) throw new IllegalArgumentException("revision must be positive");
        Objects.requireNonNull(cells, "cells");
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("too many visual cells");
        List<VisualCell> copy = new ArrayList<>(cells.size());
        Set<PositionKey> positions = new HashSet<>();
        for (VisualCell cell : cells) {
            Objects.requireNonNull(cell, "cell");
            if (!positions.add(new PositionKey(cell.localX(), cell.y(), cell.localZ())))
                throw new IllegalArgumentException("duplicate visual cell");
            copy.add(cell);
        }
        cells = List.copyOf(copy);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, AtmosphereVisualPayload value) {
        buf.writeResourceLocation(value.dimension);
        buf.writeInt(value.chunkX);
        buf.writeInt(value.chunkZ);
        buf.writeVarLong(value.revision);
        buf.writeBoolean(value.resetSnapshot);
        buf.writeBoolean(value.finalPacket);
        buf.writeVarInt(value.cells.size());
        for (VisualCell cell : value.cells) {
            buf.writeByte(cell.localX);
            buf.writeShort(cell.y);
            buf.writeByte(cell.localZ);
            buf.writeByte(cell.plasmaAlpha);
            buf.writeByte(cell.tritiumAlpha);
            buf.writeByte(cell.waterVaporAlpha);
            buf.writeByte(cell.ammoniaAlpha);
            buf.writeByte(cell.frezonAlpha);
            buf.writeByte(cell.fireIntensity);
        }
    }

    private static AtmosphereVisualPayload read(RegistryFriendlyByteBuf buf) {
        try {
            ResourceLocation dimension = buf.readResourceLocation();
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            long revision = buf.readVarLong();
            boolean reset = buf.readBoolean();
            boolean last = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CELLS)
                throw new IllegalArgumentException("invalid visual cell count: " + count);
            List<VisualCell> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                cells.add(new VisualCell(buf.readUnsignedByte(), buf.readShort(), buf.readUnsignedByte(),
                        buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                        buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte()));
            }
            if (buf.isReadable()) throw new IllegalArgumentException("trailing bytes in atmosphere visual payload");
            return new AtmosphereVisualPayload(dimension, chunkX, chunkZ, revision, reset, last, cells);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed atmosphere visual payload", exception);
        }
    }

    private record PositionKey(int x, int y, int z) { }

    /** Five ordered gas opacity bytes followed by server-authoritative fire intensity. */
    public record VisualCell(int localX, int y, int localZ, int plasmaAlpha, int tritiumAlpha,
                             int waterVaporAlpha, int ammoniaAlpha, int frezonAlpha, int fireIntensity) {
        public VisualCell {
            if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15)
                throw new IllegalArgumentException("local X/Z must be in [0,15]");
            if (y < MIN_BUILD_Y || y > MAX_BUILD_Y)
                throw new IllegalArgumentException("Y outside supported build bounds");
            checkAlpha(plasmaAlpha);
            checkAlpha(tritiumAlpha);
            checkAlpha(waterVaporAlpha);
            checkAlpha(ammoniaAlpha);
            checkAlpha(frezonAlpha);
            checkAlpha(fireIntensity);
        }

        /** Uses Minecraft's 1 m^3 atmosphere cell volume. */
        public static VisualCell from(GasMixture mixture, BlockPos pos) {
            return from(mixture, pos, 1.0d);
        }

        /** Computes opacity for a caller-specified volume, including SS14's 2.5 m^3 reference volume. */
        public static VisualCell from(GasMixture mixture, BlockPos pos, double cellVolumeCubicMeters) {
            Objects.requireNonNull(mixture, "mixture");
            Objects.requireNonNull(pos, "pos");
            int x = pos.getX() & 15;
            int z = pos.getZ() & 15;
            return new VisualCell(x, pos.getY(), z,
                    alpha(mixture, GasType.PLASMA, cellVolumeCubicMeters),
                    alpha(mixture, GasType.TRITIUM, cellVolumeCubicMeters),
                    alpha(mixture, GasType.WATER_VAPOR, cellVolumeCubicMeters),
                    alpha(mixture, GasType.AMMONIA, cellVolumeCubicMeters),
                    alpha(mixture, GasType.FREZON, cellVolumeCubicMeters), 0);
        }

        private static int alpha(GasMixture mixture, GasType type, double volume) {
            return GasVisibility.alphaByte(type, mixture.moles(type), volume);
        }

        private static void checkAlpha(int alpha) {
            if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("alpha must be an unsigned byte");
        }
    }
}
