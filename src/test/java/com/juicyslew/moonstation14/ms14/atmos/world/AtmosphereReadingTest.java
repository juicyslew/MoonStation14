package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereReadingTest {
    private static final GasMixture AIR = GasMixture.breathableAir();
    private static final GasMixture VACUUM = GasMixture.vacuum();

    @Test
    void unknownReadingUsesPersistedOverrideProvisionallyOrAmbientWithoutCreatingState() {
        AtmosphereReading fallback = AtmosphereService.readingFor(
                AtmosphereService.OwnershipKind.UNKNOWN, null, VACUUM);
        assertEquals(AtmosphereReading.Status.PROVISIONAL, fallback.status());
        assertEquals(2.7, fallback.mixture().temperatureKelvin());
        assertEquals(0.0, fallback.mixture().totalMoles());
        assertNotSame(VACUUM, fallback.mixture());

        GasMixture persisted = new GasMixture(Map.of(GasType.OXYGEN, 1.25), 250.0);
        AtmosphereReading override = AtmosphereService.readingFor(
                AtmosphereService.OwnershipKind.UNKNOWN, persisted, AIR);
        assertEquals(AtmosphereReading.Status.PROVISIONAL, override.status());
        assertEquals(1.25, override.mixture().moles(GasType.OXYGEN));
        assertEquals(250.0, override.mixture().temperatureKelvin());
        assertNotSame(persisted, override.mixture());
        assertEquals(1.25, persisted.moles(GasType.OXYGEN));
    }

    @Test
    void finiteAndExteriorReadingsAreAuthoritative() {
        GasMixture finite = new GasMixture(Map.of(GasType.NITROGEN, 3.0), 280.0);
        AtmosphereReading finiteReading = AtmosphereService.readingFor(
                AtmosphereService.OwnershipKind.FINITE, finite, AIR);
        assertEquals(AtmosphereReading.Status.FINITE, finiteReading.status());
        assertEquals(3.0, finiteReading.mixture().moles(GasType.NITROGEN));

        AtmosphereReading exterior = AtmosphereService.readingFor(
                AtmosphereService.OwnershipKind.EXTERIOR, finite, AIR);
        assertEquals(AtmosphereReading.Status.EXTERIOR, exterior.status());
        assertEquals(AIR.gasMoles(), exterior.mixture().gasMoles());
        assertEquals(AIR.temperatureKelvin(), exterior.mixture().temperatureKelvin());
    }

    @Test
    void disabledServiceDoesNotReturnPresentationGasForNullWorld() {
        assertTrue(AtmosphereService.INSTANCE.readAtmosphere(null, BlockPos.ZERO).isEmpty());
    }
}
