package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereVisualPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void codecRoundTripsAllMetadataAndUnsignedAlphaBytes() {
        var payload = new AtmosphereVisualPayload(DIMENSION, -3, 12, 9, true, false,
                List.of(new AtmosphereVisualPayload.VisualCell(15, -64, 0, 255, 128, 1, 0, 254, 0)));
        assertEquals(payload, roundTrip(AtmosphereVisualPayload.STREAM_CODEC, payload));
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "atmosphere_visual"),
                AtmosphereVisualPayload.TYPE.id());
    }

    @Test
    void boundsDuplicatesAndDeltaFlagsAreValidated() {
        var cell = new AtmosphereVisualPayload.VisualCell(1, 64, 2, 0, 0, 0, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload(
                DIMENSION, 0, 0, 1, true, true, java.util.Collections.nCopies(257, cell)));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload(
                DIMENSION, 0, 0, 1, true, true, List.of(cell, cell)));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload.VisualCell(-1, 64, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload.VisualCell(0, 2048, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload.VisualCell(0, 0, 16, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereVisualPayload.VisualCell(0, 0, 0, 256, 0, 0, 0, 0, 0));
        var continuation = new AtmosphereVisualPayload(DIMENSION, 0, 0, 1, false, false, List.of());
        assertFalse(continuation.resetSnapshot());
        assertFalse(continuation.finalPacket());
    }

    @Test
    void listIsDefensivelyCopiedAndImmutable() {
        List<AtmosphereVisualPayload.VisualCell> cells = new ArrayList<>();
        cells.add(new AtmosphereVisualPayload.VisualCell(0, 0, 0, 1, 2, 3, 4, 5, 0));
        var payload = new AtmosphereVisualPayload(DIMENSION, 0, 0, 1, false, true, cells);
        cells.clear();
        assertEquals(1, payload.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> payload.cells().clear());
    }

    @Test
    void fromUsesOnlyTheFiveOverlayGasesIncludingTritium() {
        GasMixture partial = new GasMixture(Map.of(
                GasType.PLASMA, 1.0,
                GasType.TRITIUM, 1.0,
                GasType.WATER_VAPOR, 1.0,
                GasType.AMMONIA, 2.0,
                GasType.FREZON, 4.0,
                GasType.NITROUS_OXIDE, 100.0,
                GasType.OXYGEN, 100.0), 293.15);
        var cell = AtmosphereVisualPayload.VisualCell.from(partial, new BlockPos(-1, 64, 16));
        assertEquals(15, cell.localX());
        assertEquals(0, cell.localZ());
        assertTrue(cell.plasmaAlpha() > 0 && cell.plasmaAlpha() < 255);
        assertTrue(cell.tritiumAlpha() > 0 && cell.tritiumAlpha() < 255);
        assertTrue(cell.waterVaporAlpha() > 0 && cell.waterVaporAlpha() < 255);
        assertTrue(cell.ammoniaAlpha() > 0 && cell.ammoniaAlpha() < 255);
        assertTrue(cell.frezonAlpha() > 0 && cell.frezonAlpha() < 255);
        assertTrue(cell.tritiumAlpha() > 0);

        GasMixture saturated = new GasMixture(Map.of(
                GasType.PLASMA, 2.0,
                GasType.TRITIUM, 2.0,
                GasType.WATER_VAPOR, 2.0,
                GasType.AMMONIA, 2.8,
                GasType.FREZON, 4.8), 293.15);
        var saturatedCell = AtmosphereVisualPayload.VisualCell.from(saturated, BlockPos.ZERO);
        assertEquals(255, saturatedCell.plasmaAlpha());
        assertEquals(255, saturatedCell.tritiumAlpha());
        assertEquals(255, saturatedCell.waterVaporAlpha());
        assertEquals(255, saturatedCell.ammoniaAlpha());
        assertEquals(255, saturatedCell.frezonAlpha());

        var ammoniaAtThreshold = AtmosphereVisualPayload.VisualCell.from(
                new GasMixture(Map.of(GasType.AMMONIA, 0.8), 293.15), BlockPos.ZERO);
        var frezonAtThreshold = AtmosphereVisualPayload.VisualCell.from(
                new GasMixture(Map.of(GasType.FREZON, 0.24), 293.15), BlockPos.ZERO);
        assertEquals(0, ammoniaAtThreshold.ammoniaAlpha());
        assertEquals(0, frezonAtThreshold.frezonAlpha());

        GasMixture nitrousOnly = new GasMixture(Map.of(GasType.NITROUS_OXIDE, 100.0), 293.15);
        var invisible = AtmosphereVisualPayload.VisualCell.from(nitrousOnly, BlockPos.ZERO);
        assertEquals(0, invisible.plasmaAlpha() + invisible.tritiumAlpha() + invisible.waterVaporAlpha()
                + invisible.ammoniaAlpha() + invisible.frezonAlpha());
        assertFalse(invisible.plasmaAlpha() > 0);
    }

    @Test
    void fromDefaultsToMinecraftCellVolumeAndAllowsReferenceVolumeOverride() {
        GasMixture nearThreshold = new GasMixture(Map.of(GasType.PLASMA, 0.11), 293.15);
        var minecraftCell = AtmosphereVisualPayload.VisualCell.from(nearThreshold, BlockPos.ZERO);
        var referenceCell = AtmosphereVisualPayload.VisualCell.from(nearThreshold, BlockPos.ZERO, 2.5d);

        // 0.11 mol is above the 0.1 mol threshold for 1 m^3, but rounds to quantized zero.
        // The explicit 2.5 m^3 reference volume places it below the 0.25 mol threshold.
        assertEquals(0, minecraftCell.plasmaAlpha());
        assertEquals(0, referenceCell.plasmaAlpha());

        GasMixture visibleAtMinecraftVolume = new GasMixture(Map.of(GasType.PLASMA, 0.16), 293.15);
        var visibleMinecraftCell = AtmosphereVisualPayload.VisualCell.from(visibleAtMinecraftVolume, BlockPos.ZERO);
        var invisibleReferenceCell = AtmosphereVisualPayload.VisualCell.from(
                visibleAtMinecraftVolume, BlockPos.ZERO, 2.5d);
        assertTrue(visibleMinecraftCell.plasmaAlpha() > 0);
        assertEquals(0, invisibleReferenceCell.plasmaAlpha());

        GasMixture saturatedAtMinecraftVolume = new GasMixture(Map.of(GasType.PLASMA, 2.0), 293.15);
        assertEquals(255, AtmosphereVisualPayload.VisualCell.from(saturatedAtMinecraftVolume, BlockPos.ZERO)
                .plasmaAlpha());

        GasMixture nitrousOnly = new GasMixture(Map.of(GasType.NITROUS_OXIDE, 100.0), 293.15);
        assertEquals(0, AtmosphereVisualPayload.VisualCell.from(nitrousOnly, BlockPos.ZERO).plasmaAlpha());
    }

    @Test
    void decoderRejectsOversizedCountBeforeReadingCells() {
        RegistryFriendlyByteBuf buf = buffer();
        try {
            buf.writeResourceLocation(DIMENSION);
            buf.writeInt(0);
            buf.writeInt(0);
            buf.writeVarLong(1);
            buf.writeBoolean(true);
            buf.writeBoolean(true);
            buf.writeVarInt(257);
            assertThrows(IllegalArgumentException.class, () -> AtmosphereVisualPayload.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = buffer();
        try {
            codec.encode(buf, value);
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
