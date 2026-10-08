package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.juicyslew.moonstation14.ms14.organ.RespirationState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereDamageDiagnosticCommandTest {
    @Test
    void missingExactControlledBodyHasActionableFailureMessage() {
        assertEquals("No exact controlled CHARACTER body; enter body and retry",
                AtmosphereDamageDiagnosticCommand.NO_EXACT_BODY_MESSAGE);
    }

    @Test
    void nominalAirAndTypedDamageAreReportedAsSnapshotWithoutCausalClaim() {
        var air = new GasMixture(Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 7.5,
                GasType.CARBON_DIOXIDE, 0.01), 293.15);
        List<String> lines = AtmosphereDamageDiagnosticCommand.format(new BlockPos(3, 64, -2),
                air, 310.0, new RespirationState(0.95, true, LungComponent.Phase.EXHALING),
                Map.of("asphyxiation", 0.0f), null, 20.0f, 20.0f);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Eye block: 3, 64, -2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Typed damage: {asphyxiation=0.0}")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Snapshot only")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("typed key is not provenance")));
        assertTrue(lines.contains("Respiration: initialized=true, saturation=0.9500, phase=EXHALING"));
        assertTrue(lines.contains("Bleeding: blood state unavailable"));
        assertTrue(lines.contains("Blood-loss damage ledger: 0.00"));
        assertFalse(String.join(" ", lines).toLowerCase().contains("caused"));
        assertFalse(String.join(" ", lines).toLowerCase().contains("damage cause"));
        assertTrue(lines.size() > 1);
    }

    @Test
    void missingSampleIsClearlyUnavailable() {
        var lines = AtmosphereDamageDiagnosticCommand.format(BlockPos.ZERO, null,
                null, null, null, null, 10.0f, 20.0f);
        assertTrue(lines.contains("Eye-cell gas: unavailable"));
        assertTrue(lines.contains("Body temperature: unavailable"));
        assertTrue(lines.contains("Typed damage: unavailable"));
        assertTrue(lines.contains("Respiration: unavailable"));
    }
}
