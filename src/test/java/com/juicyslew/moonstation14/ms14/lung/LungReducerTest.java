package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.BreathExchange;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LungReducerTest {
    private static CharacterData.LungsData policy() {
        return new CharacterData.LungsData(2, 0.5, 6, 1144, 5, 5, -2, 2, 0, 1, 1, true);
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

    @Test void invalidStateFailsClosedAndOldHighSaturationCanBeReadForExplicitReconciliation() {
        var old = LungComponent.from(GasMixture.vacuum(), 98, true);
        assertEquals(98, LungComponent.CODEC.parse(JsonOps.INSTANCE,
                LungComponent.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow()).getOrThrow().saturation());
        assertTrue(LungSystem.advance(old, policy(), GasMixture.vacuum(), false,
                (e, n) -> fail("no exchange before reconciliation")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LungComponent.from(GasMixture.vacuum(), -3, true));
        var negative = LungComponent.from(GasMixture.vacuum(), -2, true);
        assertEquals(negative, LungComponent.CODEC.parse(JsonOps.INSTANCE,
                LungComponent.CODEC.encodeStart(JsonOps.INSTANCE, negative).getOrThrow()).getOrThrow());
    }
}
