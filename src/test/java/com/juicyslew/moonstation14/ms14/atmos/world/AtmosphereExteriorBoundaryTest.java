package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.ImmutableAtmosphereBoundary;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereExteriorBoundaryTest {
    @Test
    void underOverhangFiniteCellCanDrainIntoImmutableAdjacentSkySink() {
        GasMixture finiteRoomCell = new GasMixture(Map.of(GasType.OXYGEN, 5.0, GasType.NITROGEN, 15.0), 293.15);
        GasMixture immutableExterior = GasMixture.vacuum();

        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                finiteRoomCell, immutableExterior, 0.125, true);

        assertNotEquals(finiteRoomCell.gasMoles(), result.finiteAfter().gasMoles(),
                "a finite cell beneath an overhang may exchange through its adjacent sky sink");
        assertEquals(immutableExterior.gasMoles(), GasMixture.vacuum().gasMoles(),
                "the exterior cell is a snapshot and is never written");
        assertEquals(finiteRoomCell.moles(GasType.OXYGEN) - result.finiteAfter().moles(GasType.OXYGEN),
                result.perGasExported().get(GasType.OXYGEN), 1.0e-12);
        assertTrue(result.perGasExported().get(GasType.OXYGEN) > 0.0);
        assertTrue(result.perGasExported().get(GasType.NITROGEN) > 0.0);
    }

    @Test
    void breathableExteriorReplenishesDepletedFiniteCellWithoutExportingToVacuum() {
        GasMixture depleted = GasMixture.vacuum();
        GasMixture ambient = GasMixture.breathableAir();

        ImmutableAtmosphereBoundary.Result result = ImmutableAtmosphereBoundary.exchange(
                depleted, ambient, 0.125, false);

        assertTrue(result.finiteAfter().moles(GasType.OXYGEN) > 0.0);
        assertTrue(result.finiteAfter().moles(GasType.NITROGEN) > 0.0);
        assertEquals(GasMixture.breathableAir().gasMoles(), ambient.gasMoles(),
                "immutable exterior snapshot must not be consumed by replenishment");
        assertTrue(result.perGasExported().getOrDefault(GasType.OXYGEN, 0.0) < 0.0);
        assertTrue(result.perGasExported().getOrDefault(GasType.NITROGEN, 0.0) < 0.0);
    }

}
