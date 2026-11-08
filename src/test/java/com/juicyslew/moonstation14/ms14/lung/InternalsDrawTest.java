package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankDrawHelper;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InternalsDrawTest {
    private static GasTankData profile(double outlet) {
        return new GasTankData(.01, 293.15, Map.of(GasType.OXYGEN, 100d),
                ModGasTanks.id("oxygen_tank"), outlet, 30d);
    }

    @Test void outputDrawIsFiniteProportionalAndLeavesPhysicalTankVolumeAlone() {
        var tank = new GasTankContents(ModGasTanks.OXYGEN_ID, profile(21.3).initialMixture());
        var draw = GasTankDrawHelper.drawOutput(tank, profile(21.3), 1d, .01d);
        assertEquals(GasMixture.molesForPressureDelta(21.3d, .001d, 293.15), draw.inhaled().totalMoles(), 1e-6);
        assertEquals(100d - draw.inhaled().totalMoles(), draw.remaining().mixture().totalMoles(), 1e-4);
        assertEquals(.01d, draw.remaining().mixture().volumeCubicMeters(), 1e-8);
        assertEquals(.001d, draw.inhaled().volumeCubicMeters(), 1e-9);
    }

    @Test void emptyAndZeroOutletProduceKnownEmptyPacketsWithoutAmbientFill() {
        var zero = profile(0d);
        var filled = new GasTankContents(ModGasTanks.OXYGEN_ID, zero.initialMixture());
        var zeroDraw = GasTankDrawHelper.drawOutput(filled, zero, 2d);
        assertEquals(0d, zeroDraw.inhaled().totalMoles());
        assertEquals(filled.mixture().totalMoles(), zeroDraw.remaining().mixture().totalMoles());

        var empty = new GasTankContents(ModGasTanks.OXYGEN_ID,
                new GasMixture(Map.of(), 293.15, .01));
        var emptyDraw = GasTankDrawHelper.drawOutput(empty, profile(21.3), 2d);
        assertEquals(0d, emptyDraw.inhaled().totalMoles());
        assertEquals(0d, emptyDraw.remaining().mixture().totalMoles());
    }

    @Test void partialDrawUsesOnlyRequestedReceiverCapacity() {
        var p = profile(21.3);
        var contents = new GasTankContents(ModGasTanks.OXYGEN_ID, p.initialMixture());
        var draw = GasTankDrawHelper.drawOutput(contents, p, 1d, 0d);
        assertEquals(0d, draw.inhaled().totalMoles());
        assertEquals(contents.mixture().totalMoles(), draw.remaining().mixture().totalMoles());
    }

    @Test void finalUndersizedTankChargeIsReturnedAsPacketWithoutAtmosphereCleanupLoss() {
        var p = profile(21.3);
        // Below the atmosphere species-cleanup floor, but still positive finite tank inventory.
        double tail = GasMixture.GAS_MIN_MOLES / 2d;
        var contents = new GasTankContents(ModGasTanks.OXYGEN_ID,
                new GasMixture(Map.of(GasType.OXYGEN, tail), 293.15, .01));
        double storedTail = contents.mixture().moles(GasType.OXYGEN);
        var draw = GasTankDrawHelper.drawOutput(contents, p, .5d);
        assertEquals(storedTail, draw.inhaled().moles(GasType.OXYGEN), 0d);
        assertEquals(.0005d, draw.inhaled().volumeCubicMeters(), 1e-9);
        assertEquals(0d, draw.remaining().mixture().totalMoles(), 0d);
        assertEquals(contents.mixture().totalMoles(),
                draw.inhaled().totalMoles() + draw.remaining().mixture().totalMoles(), 0d);
    }

    @Test void rejectedValidatorLeavesCanonicalContentsUntouchedAndOwnershipDriftRejectsPublication() {
        var p = profile(21.3);
        var contents = new GasTankContents(ModGasTanks.OXYGEN_ID, p.initialMixture());
        double before = contents.mixture().totalMoles();
        assertTrue(InternalsSystem.validatedDraw(contents, p, 1d, 1d, packet -> false).isEmpty());
        assertEquals(before, contents.mixture().totalMoles());
        var accepted = InternalsSystem.validatedDraw(contents, p, 1d, 1d, packet -> true).orElseThrow();
        assertTrue(accepted.inhaled().totalMoles() <= 1d + 1e-6);
        assertEquals(.001d, accepted.inhaled().volumeCubicMeters(), 1e-9);

        var source = new InternalsSystem.SourceHandle("back", false, "tank-1", net.minecraft.world.item.ItemStack.EMPTY,
                contents, p, null);
        assertTrue(InternalsSystem.stillSelected(source, null, "tank-1"));
        assertFalse(InternalsSystem.stillSelected(source, null, "replacement-token"));
    }
    @Test void actionProviderStateTracksOnlyExactSelectedTankAcrossReplacement() {
        assertTrue(InternalsSystem.providerSelected("tank-a", "tank-a"));
        assertFalse(InternalsSystem.providerSelected("tank-a", "tank-b"));
        assertFalse(InternalsSystem.providerSelected("replacement-token", "tank-a"));
        assertFalse(InternalsSystem.providerSelected(null, "tank-a"));
    }

    @Test void onlyInhalingPhaseUsesInternalsAndEmptyKnownSourceStillValidatesProgress() {
        assertTrue(InternalsSystem.useTankForPhase(LungComponent.Phase.INHALING, true));
        assertFalse(InternalsSystem.useTankForPhase(LungComponent.Phase.EXHALING, true));
        assertFalse(InternalsSystem.useTankForPhase(LungComponent.Phase.INHALING, false));

        var zero = profile(0d);
        var empty = new GasTankContents(ModGasTanks.OXYGEN_ID, new GasMixture(Map.of(), 293.15, .01));
        boolean[] validated = {false};
        var draw = InternalsSystem.validatedDraw(empty, zero, .5d, 0d,
                packet -> { validated[0] = true; return packet.totalMoles() == 0; });
        assertTrue(validated[0]);
        assertTrue(draw.isPresent());
        assertEquals(.0005d, draw.orElseThrow().inhaled().volumeCubicMeters(), 1e-9);
    }

    @Test void typedInternalsCompositionIsRegisteredAndSchemaAcceptsPresenceMarker() {
        assertTrue(com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry.registered("Internals"));
        var json = new com.google.gson.JsonObject();
        json.addProperty("type", "Internals");
        assertDoesNotThrow(() -> com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit.auditComponent(json, "$.components[0]"));
    }

    @Test void exhaleDelegateReceivesLungGasAndNeverAnInternalsTankCredit() {
        var respiratory = new com.juicyslew.moonstation14.ms14.character.components.RespiratorPolicy(
                2d, .5d, 5d, 5d, -2d, 2d, 0d, 1d, 1d, true);
        var organ = new com.juicyslew.moonstation14.ms14.organ.OrganData.Lung(100d, 1d, Map.of(), 0d);
        var policy = new LungRuntimePolicy(respiratory, organ);
        GasMixture lungGas = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 1d), 293.15);
        var current = LungComponent.from(lungGas, 4d, true, LungComponent.Phase.EXHALING);
        GasMixture[] offered = {null};
        double[] requested = {-1d};
        var result = LungSystem.advance(current, policy, GasMixture.breathableAir(), 1d, false,
                (exhaled, amount) -> {
                    offered[0] = exhaled;
                    requested[0] = amount;
                    return Optional.of(GasMixture.vacuum());
                }).orElseThrow();
        assertEquals(1d, offered[0].moles(GasType.CARBON_DIOXIDE), 1e-6);
        assertEquals(0d, requested[0]);
        assertEquals(0d, result.mixture().totalMoles());
        assertEquals(LungComponent.Phase.INHALING, result.phase());
        assertFalse(InternalsSystem.useTankForPhase(current.phase(), true));
    }
}
