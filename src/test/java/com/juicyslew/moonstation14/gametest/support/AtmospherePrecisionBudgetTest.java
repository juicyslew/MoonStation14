package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmospherePrecisionBudgetTest {
    @Test
    void sourceComponentBoundTracksSimplePathEdgesAndKeepsLegacyGlobalDefault() {
        assertEquals(16_002, AtmospherePrecisionBudget.operations(2, 66, 1));
        assertEquals(8_706, AtmospherePrecisionBudget.operations(2, 33, 1, 18));
        assertEquals(4_353, AtmospherePrecisionBudget.operations(1, 33, 0, 18));
        assertTrue(AtmospherePrecisionBudget.operations(2, 33, 1, 18)
                < AtmospherePrecisionBudget.operations(2, 66, 1));
    }

    @Test
    void completeEighteenCellSourceBudgetDistinguishesCapturedSignalFromBroaderFixtureBudget() {
        var sourceBefore = inventory(18);
        var sourceAfter = inventoryWithPlasmaRemovedFromFirstCell(18, .24);
        var entireFixtureBefore = inventory(36);
        var entireFixtureAfter = inventoryWithPlasmaRemovedFromFirstCell(36, .24);

        double local = sourceBefore.moleBudget(GasType.PLASMA, sourceAfter,
                AtmospherePrecisionBudget.operations(2, 33, 1, 18));
        double legacy = entireFixtureBefore.moleBudget(GasType.PLASMA, entireFixtureAfter,
                AtmospherePrecisionBudget.operations(2, 66, 1));

        assertTrue(local < .24, "source component rounding bound must not hide the captured packet: " + local);
        assertTrue(legacy >= .24, "regression fixture must demonstrate the legacy bound can hide it: " + legacy);
    }

    @Test
    void explicitLocalBoundRejectsUnknownComponentAndNegativeInputs() {
        assertThrows(IllegalArgumentException.class, () -> AtmospherePrecisionBudget.operations(1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> AtmospherePrecisionBudget.operations(1, -1, 0, 18));
        assertThrows(IllegalArgumentException.class, () -> AtmospherePrecisionBudget.operations(1, 0, -1, 18));
    }

    @Test
    void sourceAndPipeUseOneConservationAndSignalBound() {
        double operations = AtmospherePrecisionBudget.aggregateOperations(2, 18, 11, 20, .6);
        assertEquals(9_321, operations); // 1 + 2*(256*17) + 2*(11*(2*9+2) + 8*(9+2))
        var before = splitInventory(36, 0);
        var conserved = splitInventory(35.76, .24);
        var missingPacket = splitInventory(35.76, 0);
        var createdPacket = splitInventory(36, .24);
        assertTrue(AtmospherePrecisionBudget.conservedAndSignalExceedsBudget(
                before, conserved, GasType.PLASMA, .24, operations));
        assertFalse(AtmospherePrecisionBudget.conservedAndSignalExceedsBudget(
                before, missingPacket, GasType.PLASMA, .24, operations),
                "same source-plus-pipe bound must reject a missing full captured packet");
        assertFalse(AtmospherePrecisionBudget.conservedAndSignalExceedsBudget(
                before, createdPacket, GasType.PLASMA, .24, operations),
                "same source-plus-pipe bound must reject a created full packet");
        assertThrows(IllegalArgumentException.class,
                () -> AtmospherePrecisionBudget.aggregateOperations(1, 18, 0, 20, .6));
        assertThrows(IllegalArgumentException.class,
                () -> AtmospherePrecisionBudget.aggregateOperations(1, 18, 11, -1, .6));
    }

    private static AtmospherePrecisionBudget.Inventory splitInventory(double sourcePlasma, double pipePlasma) {
        return AtmospherePrecisionBudget.Inventory.capture(List.of(
                new GasMixture(Map.of(GasType.PLASMA, sourcePlasma), 293.15),
                new GasMixture(Map.of(GasType.PLASMA, pipePlasma), 293.15)));
    }

    private static AtmospherePrecisionBudget.Inventory inventory(int cells) {
        return AtmospherePrecisionBudget.Inventory.capture(mixtures(cells, 0));
    }

    private static AtmospherePrecisionBudget.Inventory inventoryWithPlasmaRemovedFromFirstCell(int cells, double removed) {
        return AtmospherePrecisionBudget.Inventory.capture(mixtures(cells, removed));
    }

    private static List<GasMixture> mixtures(int cells, double removedFromFirst) {
        List<GasMixture> result = new ArrayList<>();
        for (int i = 0; i < cells; i++) {
            double plasma = 2.0 - (i == 0 ? removedFromFirst : 0.0);
            result.add(new GasMixture(Map.of(GasType.PLASMA, plasma), 293.15));
        }
        return result;
    }
}
