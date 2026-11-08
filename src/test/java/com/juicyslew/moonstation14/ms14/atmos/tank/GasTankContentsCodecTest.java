package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GasTankContentsCodecTest {
    private static GasTankContents sample() {
        return new GasTankContents(ModGasTanks.PLASMA_FILLED_ID,
                new GasMixture(Map.of(GasType.PLASMA, 2.05137905, GasType.OXYGEN, 0.123456), 301.25, 0.005));
    }

    @Test void persistentCodecRetainsProfileAndFiniteMixture() {
        GasTankContents original = sample();
        var json = GasTankContents.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = GasTankContents.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(original.prototypeId(), decoded.prototypeId());
        assertMixture(original.mixture(), decoded.mixture());
    }

    @Test void networkCodecRetainsProfileAndMultipleGasSpecies() {
        GasTankContents original = sample();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            GasTankContents.STREAM_CODEC.encode(buffer, original);
            GasTankContents decoded = GasTankContents.STREAM_CODEC.decode(buffer);
            assertEquals(original.prototypeId(), decoded.prototypeId());
            assertMixture(original.mixture(), decoded.mixture());
        } finally { buffer.release(); }
    }

    @Test void mixtureMutationPreservesTankVolumeAndProfileAndRejectsCapacityChange() {
        GasTankContents original = sample();
        GasTankContents changed = original.withMixture(original.mixture().withGasDelta(GasType.OXYGEN, 0.2));
        assertEquals(original.prototypeId(), changed.prototypeId());
        assertEquals(0.005, changed.mixture().volumeCubicMeters(), 1e-9);
        assertEquals(0.123456, original.mixture().moles(GasType.OXYGEN), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> original.withMixture(new GasMixture(Map.of(), 293.15, 0.006)));
    }

    private static void assertMixture(GasMixture expected, GasMixture actual) {
        assertEquals(expected.volumeCubicMeters(), actual.volumeCubicMeters(), 1e-9);
        assertEquals(expected.temperatureKelvin(), actual.temperatureKelvin(), 1e-3);
        assertEquals(expected.moles(GasType.PLASMA), actual.moles(GasType.PLASMA), 1e-6);
        assertEquals(expected.moles(GasType.OXYGEN), actual.moles(GasType.OXYGEN), 1e-6);
    }
}
