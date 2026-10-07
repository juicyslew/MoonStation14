package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.doors.prototype.ElectrifiedData;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AirlockElectrificationSystemTest {
    private static final ElectrifiedData DEFAULTS = ElectrifiedData.AIRLOCK_DEFAULTS;

    @Test
    void componentCodecResolvesAirlockElectrifiedDefaults() {
        var resolved = ElectrifiedData.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{}")).result().orElseThrow();
        assertEquals(DEFAULTS, resolved);
        assertFalse(resolved.enabled());
        assertTrue(resolved.usesApcPower());
        assertTrue(resolved.requirePower());
        assertEquals(7.5f, resolved.shockDamage());
        assertEquals(5f, resolved.shockSeconds());
        assertEquals(1f, resolved.probability());
        assertEquals(1f, resolved.siemensCoefficient());
    }

    @Test
    void cutMendAndPulseUpdateOrderingPreservesWireElectrificationQuirks() {
        var state = AirlockElectrificationSystem.State.initial(DEFAULTS);
        var firstCut = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.CUT, 10, DEFAULTS, null, true).state();
        assertTrue(firstCut.enabled()); // enabled before count/power update
        state = AirlockElectrificationSystem.updateAfterWireMutation(firstCut, false, false, true, 10, DEFAULTS);
        assertTrue(state.enabled());

        var secondCut = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.CUT, 11, DEFAULTS, null, true).state();
        assertTrue(secondCut.enabled()); // last shock admission precedes poweroff
        state = AirlockElectrificationSystem.updateAfterWireMutation(secondCut, true, false, false, 11, DEFAULTS);
        assertFalse(state.enabled());

        state = AirlockElectrificationSystem.State.initial(DEFAULTS);
        state = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.CUT, 20, DEFAULTS, null, true).state();
        state = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.CUT, 21, DEFAULTS, null, true).state();
        state = AirlockElectrificationSystem.updateAfterWireMutation(state, true, false, false, 21, DEFAULTS);
        state = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.MEND, 22, DEFAULTS, null, true).state();
        state = AirlockElectrificationSystem.updateAfterWireMutation(state, false, false, true, 22, DEFAULTS);
        assertTrue(state.enabled()); // one mended wire restores power, electrification remains
        state = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.MEND, 23, DEFAULTS, null, true).state();
        state = AirlockElectrificationSystem.updateAfterWireMutation(state, false, true, true, 23, DEFAULTS);
        assertFalse(state.enabled());

        state = AirlockElectrificationSystem.State.initial(DEFAULTS);
        state = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.PULSE, 100, DEFAULTS, null, true).state();
        assertEquals(0, state.pulseCancelAt(), "wire mutation itself does not run UpdateElectrocution");
        // PowerWireAction.Update invokes UpdateElectrocution after the action.
        state = AirlockElectrificationSystem.updateAfterWireMutation(state, false, false, true, 100, DEFAULTS);
        assertEquals(700, state.pulseCancelAt());
        var mendedDuringPulse = AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.MEND, 200, DEFAULTS, null, true).state();
        assertEquals(0, mendedDuringPulse.pulseCancelAt()); // Mend cancels electrification timer, latch persists
        assertTrue(mendedDuringPulse.pulsed());
        var update = AirlockElectrificationSystem.updateAfterWireMutation(mendedDuringPulse,
                false, true, true, 200, DEFAULTS);
        assertEquals(800, update.pulseCancelAt()); // upstream update starts cancel timer again
        var callback = AirlockElectrificationSystem.onPulseCancel(update, true);
        assertFalse(callback.enabled());
    }

    @Test
    void twoDoorHalvesShareOneContactEdgeUntilContactEnds() {
        var edges = new AirlockElectrificationSystem.ContactEdges();
        UUID body = UUID.randomUUID();
        assertTrue(edges.begin(body));
        assertFalse(edges.begin(body));
        edges.end(body);
        assertTrue(edges.begin(body));
    }

    @Test
    void missingActorOrFreshFullPowerCannotProduceSourceShock() {
        assertFalse(AirlockElectrificationSystem.freshFullyPowered(false,true,20,20));
        assertFalse(AirlockElectrificationSystem.freshFullyPowered(true,true,19.99,20));
        assertFalse(AirlockElectrificationSystem.freshFullyPowered(true,false,20,20));
        assertTrue(AirlockElectrificationSystem.freshFullyPowered(true,true,20,20));
        var state=AirlockElectrificationSystem.State.initial(DEFAULTS);
        var attempted=AirlockElectrificationSystem.beforePowerWireAction(state,
                AirlockElectrificationSystem.WireAction.CUT,1,DEFAULTS,null,false);
        assertTrue(attempted.state().enabled()); // accepted state mutation is independent from shock admission
        assertEquals(com.juicyslew.moonstation14.ms14.effect.EffectResult.SKIPPED_UNSUPPORTED,
                attempted.attemptedShock());
        assertEquals(com.juicyslew.moonstation14.ms14.effect.EffectResult.SKIPPED_UNSUPPORTED,
                AirlockElectrificationSystem.contact(attempted.state(),DEFAULTS,
                        AirlockElectrificationSystem.Contact.TOUCH,null,false));
    }
}
