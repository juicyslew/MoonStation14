package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.actions.prototype.ActionHandler;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BodyActionAdmissionTest {
    private static ActionPrototype profile(boolean consciousness, boolean interaction) {
        return new ActionPrototype("test", ResourceLocation.fromNamespaceAndPath("test", "icon"), Optional.empty(),
                ActionHandler.COMBAT_TOGGLE, ActionPrototype.Kind.INSTANT, interaction, consciousness, 0, 0);
    }

    @Test void mentalCombatSkipsBothGates() {
        assertEquals(BodyActionAdmission.Reason.ENABLED,
                BodyActionAdmission.evaluate(profile(false, false), false, false).reason());
    }

    @Test void screamRequiresConsciousnessButNotInteraction() {
        assertTrue(BodyActionAdmission.evaluate(profile(true, false), true, false).enabled());
        assertEquals(BodyActionAdmission.Reason.UNCONSCIOUS,
                BodyActionAdmission.evaluate(profile(true, false), false, true).reason());
    }

    @Test void ordinaryDebugActionRequiresBothGates() {
        assertTrue(BodyActionAdmission.evaluate(profile(true, true), true, true).enabled());
        assertEquals(BodyActionAdmission.Reason.CANNOT_INTERACT,
                BodyActionAdmission.evaluate(profile(true, true), true, false).reason());
    }

    @Test void nullProfileAndUnsupportedGatesAreDenied() {
        assertEquals(BodyActionAdmission.Reason.UNAVAILABLE,
                BodyActionAdmission.evaluate(null, true, true).reason());
        assertEquals(BodyActionAdmission.Reason.UNCONSCIOUS,
                BodyActionAdmission.evaluate(profile(true, true), false, false).reason());
    }
}
