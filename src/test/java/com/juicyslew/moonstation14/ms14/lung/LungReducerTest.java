package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorPolicy;
import com.juicyslew.moonstation14.ms14.organ.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LungReducerTest {
    private static LungRuntimePolicy policy() {
        return new LungRuntimePolicy(new RespiratorPolicy(2, 0.5, 5, 5, -2, 2, 0, 1, 1, true),
                new OrganData.Lung(6, 1144, Map.of(), 0));
    }

    @Test void volumeUsesActualStrictSampleNotFixedMoleRate() {
        GasMixture air = GasMixture.breathableAir();
        double request = LungSystem.requestedMoles(air, 0.5, 6);
        assertEquals(101325d / (8.31446261815324d * 293.15d) * 0.0005, request, 1e-12);
        assertEquals(0.020786, request, 0.00002);
        assertEquals(request / 2, LungSystem.requestedMoles(air.withScaledMoles(0.5), 0.5, 6), 1e-12);
        assertEquals(0, LungSystem.requestedMoles(GasMixture.vacuum(), 0.5, 6));
        assertEquals(0.001, LungSystem.requestedMoles(air, 0.5, 0.001));
        assertThrows(IllegalArgumentException.class, () -> LungSystem.requestedMoles(air, Double.NaN, 6));
    }

    @Test void zeroKelvinGasCannotBecomeVacuumOrReachExchange() {
        GasMixture impossible = new GasMixture(Map.of(GasType.OXYGEN, 1d), 0d);
        assertTrue(LungSystem.safeRequestedMoles(impossible, 0.5, 6).isEmpty());
        assertTrue(LungSystem.safeRequestedMoles(impossible, 0.5, 0).isEmpty(),
                "preflight must reject even before the lung capacity is known");
        assertTrue(LungSystem.safeRequestedMoles(GasMixture.breathableAir(), 0.5, -1).isEmpty());
        assertTrue(LungSystem.safeRequestedMoles(GasMixture.breathableAir(), 0.5, Double.NaN).isEmpty());
        assertEquals(0, LungSystem.safeRequestedMoles(GasMixture.vacuum(), 0.5, 6).orElseThrow());
        var original = LungComponent.from(GasMixture.vacuum(), 5, true);
        assertTrue(LungSystem.advance(original, policy(), impossible, false,
                (exhaled, request) -> fail("invalid sample must not exchange")).isEmpty());
        assertEquals(LungComponent.Phase.INHALING, original.phase());
        assertEquals(5, original.saturation());
    }

    @Test void vacuumCrossesZeroOnThirdDueUpdateAndStaysAtMinimum() {
        var current = LungComponent.from(GasMixture.vacuum(), 5, true);
        for (int i = 0; i < 5; i++) {
            current = LungSystem.advance(current, policy(), GasMixture.vacuum(), false,
                    (exhaled, requested) -> {
                        assertEquals(0, requested);
                        return Optional.of(GasMixture.vacuum());
                    }).orElseThrow();
            assertEquals(i == 0 ? 3 : i == 1 ? 1 : i == 2 ? -1 : -2, current.saturation());
            assertEquals(i % 2 == 0 ? LungComponent.Phase.EXHALING : LungComponent.Phase.INHALING, current.phase());
            assertEquals(0, current.mixture().totalMoles());
        }
        assertEquals(-2, LungReducer.deplete(-2, 2, -2));
    }

    @Test void finiteAirRefillsFiveAndReturnsConvertedGasExactlyOnNextUpdate() {
        var room = GasMixture.breathableAir();
        var initial = LungComponent.from(GasMixture.vacuum(), 5, true);
        double requested = LungSystem.requestedMoles(room, 0.5, 6);
        var exchange = BreathExchange.calculate(room, requested, GasMixture.vacuum()).orElseThrow();
        var inhaled = LungSystem.advance(initial, policy(), room, false,
                (exhaled, amount) -> {
                    assertEquals(requested, amount, 1e-12);
                    assertEquals(0, exhaled.totalMoles());
                    return Optional.of(exchange.inhaled());
                }).orElseThrow();
        assertEquals(5, inhaled.saturation());
        assertEquals(LungComponent.Phase.EXHALING, inhaled.phase());
        assertEquals(requested, inhaled.mixture().totalMoles(), 1e-10);
        assertEquals(exchange.inhaled().moles(GasType.OXYGEN), inhaled.mixture().moles(GasType.CARBON_DIOXIDE), 1e-10);
        var returned = BreathExchange.calculate(exchange.roomAfter(), 0, inhaled.mixture()).orElseThrow();
        var empty = LungSystem.advance(inhaled, policy(), exchange.roomAfter(), false,
                (exhaled, amount) -> {
                    assertEquals(0, amount);
                    assertEquals(inhaled.mixture().gasMoles(), exhaled.gasMoles());
                    return Optional.of(returned.inhaled());
                }).orElseThrow();
        assertTrue(empty.gasMoles().isEmpty());
        assertEquals(3, empty.saturation());
        for (GasType gas : GasType.values())
            assertEquals(room.moles(gas) + (gas == GasType.OXYGEN ? -exchange.inhaled().moles(gas)
                    : gas == GasType.CARBON_DIOXIDE ? exchange.inhaled().moles(GasType.OXYGEN) : 0),
                    returned.roomAfter().moles(gas), 1e-9);
    }

    @Test void proportionalHalfLiterSampleDoesNotRejectRoundingAtFiveMoles() {
        GasMixture room = new GasMixture(Map.of(GasType.OXYGEN, 1.05,
                GasType.NITROGEN, 3.95), 293.15);
        double requested = LungSystem.requestedMoles(room, 0.5, 4.2);
        var exchange = BreathExchange.calculate(room, requested, GasMixture.vacuum()).orElseThrow();
        assertEquals(0.0025, requested, 1e-12);
        assertTrue(exchange.inhaled().totalMoles() > requested,
                "fixture must exercise species-sum floating-point rounding");
        var inhaled = LungSystem.advance(LungComponent.from(GasMixture.vacuum(), 1, true),
                policy(), room, false, (exhaled, amount) -> Optional.of(exchange.inhaled()));
        assertTrue(inhaled.isPresent());
        assertEquals(exchange.inhaled().totalMoles(), inhaled.orElseThrow().mixture().totalMoles(), 1e-12);
    }

    @Test void invalidStateFailsClosedAndBodyCodecRetainsHighSaturationForExplicitReconciliation() {
        var old = LungComponent.from(GasMixture.vacuum(), 98, true);
        BodyState saved = new BodyState(List.of(), RespirationState.from(old));
        assertEquals(98, BodyAttachment.CODEC.parse(JsonOps.INSTANCE,
                BodyAttachment.CODEC.encodeStart(JsonOps.INSTANCE, new BodyAttachment(saved)).getOrThrow())
                .getOrThrow().state().respiration().saturation());
        assertTrue(LungSystem.advance(old, policy(), GasMixture.vacuum(), false,
                (e, n) -> fail("no exchange before reconciliation")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LungComponent.from(GasMixture.vacuum(), -3, true));
        var negative = LungComponent.from(GasMixture.vacuum(), -2, true);
        BodyState depleted = saved.updateRespiration(RespirationState.from(negative));
        assertEquals(depleted, BodyAttachment.CODEC.parse(JsonOps.INSTANCE,
                BodyAttachment.CODEC.encodeStart(JsonOps.INSTANCE, new BodyAttachment(depleted)).getOrThrow())
                .getOrThrow().state());
    }

    @Test void reducerResultCanBeSplitIntoIndependentPersistedBodyAndOrganState() {
        var previous = LungComponent.from(GasMixture.vacuum(), 5, true);
        var reduced = LungSystem.advance(previous, policy(), GasMixture.vacuum(), false,
                (exhaled, requested) -> Optional.of(GasMixture.vacuum())).orElseThrow();
        assertEquals(new RespirationState(3, true, LungComponent.Phase.EXHALING), RespirationState.from(reduced));
        assertEquals(LungOrganState.EMPTY, LungOrganState.from(reduced));
        var missingLung = LungSystem.advance(previous, policy(), GasMixture.vacuum(), true,
                (exhaled, requested) -> fail("a body without lungs cannot exchange gas")).orElseThrow();
        assertEquals(3, missingLung.saturation());
        assertEquals(previous.phase(), missingLung.phase());
    }

    @Test void catalogOrphanLungIsFunctionallyAbsentButRemainsPersisted() {
        ResourceLocation lungId = ResourceLocation.parse("moonstation14:old_lung");
        ResourceLocation heartId = ResourceLocation.parse("moonstation14:old_heart");
        OrganInstance lung = new OrganInstance(UUID.randomUUID(), lungId, OrganCategory.LUNGS,
                new LungOrganState(Map.of("oxygen", 0.25), 290));
        OrganInstance heart = new OrganInstance(UUID.randomUUID(), heartId, OrganCategory.HEART, null);
        BodyState saved = new BodyState(List.of(lung, heart), new RespirationState(5, true, LungComponent.Phase.INHALING));
        PrototypeCatalog<OrganData> old = new PrototypeCatalog<>(Map.of(
                lungId, new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0))),
                heartId, new OrganData(OrganCategory.HEART, Optional.empty())));
        PrototypeCatalog<OrganData> withoutLung = new PrototypeCatalog<>(Map.of(
                heartId, new OrganData(OrganCategory.HEART, Optional.empty())));
        PrototypeCatalog<OrganData> withoutHeart = new PrototypeCatalog<>(Map.of(
                lungId, new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0)))));
        PrototypeCatalog<OrganData> mismatched = new PrototypeCatalog<>(Map.of(
                lungId, new OrganData(OrganCategory.HEART, Optional.empty())));
        assertEquals(Optional.of(lung), LungSystem.activeLung(saved, old));
        assertEquals(Optional.of(lung), LungSystem.activeLung(saved, withoutHeart),
                "an unrelated stale heart must not stop breathing");
        assertTrue(LungSystem.activeLung(saved, withoutLung).isEmpty());
        assertTrue(LungSystem.activeLung(saved, mismatched).isEmpty());
        assertEquals(6, LungSystem.currentLung(lung, old).orElseThrow().maxLungMoles());
        PrototypeCatalog<OrganData> reloaded = new PrototypeCatalog<>(Map.of(
                lungId, new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(0.1, 42, Map.of(), 0)))));
        assertEquals(0.1, LungSystem.currentLung(lung, reloaded).orElseThrow().maxLungMoles());
        assertTrue(LungSystem.currentLung(lung, withoutLung).isEmpty());
        OrganInstance swapped = new OrganInstance(UUID.randomUUID(), heartId, OrganCategory.LUNGS, LungOrganState.EMPTY);
        assertTrue(LungSystem.currentLung(swapped, reloaded).isEmpty());
        var missingBreath = LungSystem.advance(LungComponent.from(GasMixture.vacuum(), 5, true),
                policy(), GasMixture.breathableAir(), true,
                (exhaled, requested) -> fail("orphan must never exchange with room"));
        assertEquals(3, missingBreath.orElseThrow().saturation());
        assertEquals(lung, saved.find(OrganCategory.LUNGS).orElseThrow());
        assertEquals(heart, saved.find(OrganCategory.HEART).orElseThrow());
    }
}
