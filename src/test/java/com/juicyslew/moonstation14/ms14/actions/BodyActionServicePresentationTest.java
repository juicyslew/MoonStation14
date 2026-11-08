package com.juicyslew.moonstation14.ms14.actions;

import com.juicyslew.moonstation14.ms14.actions.prototype.ActionHandler;
import com.juicyslew.moonstation14.ms14.actions.prototype.ActionPrototype;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodyActionServicePresentationTest {
    private static ActionPrototype profile(String displayName, ActionHandler handler) {
        return new ActionPrototype(displayName, ResourceLocation.fromNamespaceAndPath("moonstation14", "test"),
                Optional.empty(), handler, ActionPrototype.Kind.TOGGLE, false, false, 0, 0);
    }

    @Test void existingActionLabelsRemainUntouchedWhileNewProvidersGetSlotAwareLabels() {
        var combat = profile("action.moonstation14.combat_mode_toggle", ActionHandler.COMBAT_TOGGLE);
        var debug = profile("action.moonstation14.debug_pulse", ActionHandler.DEBUG_PULSE);
        assertEquals(combat.displayName(), BodyActionService.providerPresentationName(combat, "equipment:head:cap", false));
        assertEquals(debug.displayName(), BodyActionService.providerPresentationName(debug, "equipment:head:cap", false));
        assertEquals(combat.displayName(), BodyActionService.providerPresentationName(combat, "equipment:head:cap", true));
        assertEquals(debug.displayName(), BodyActionService.providerPresentationName(debug, "equipment:head:cap", true));

        var internals = profile("action.moonstation14.toggle_internals", ActionHandler.INTERNALS_TOGGLE);
        assertEquals("action.moonstation14.toggle_internals.source.back",
                BodyActionService.providerPresentationName(internals, "equipment:back:token-a", false));
        assertEquals("action.moonstation14.toggle_internals.source.pocket1",
                BodyActionService.providerPresentationName(internals, "equipment:pocket1:token-b", false));
        assertEquals("action.moonstation14.toggle_internals.source.held.lefthand",
                BodyActionService.providerPresentationName(internals, "hand:leftHand:token-c", false));
        assertEquals("action.moonstation14.toggle_internals.source.back.low_pressure",
                BodyActionService.providerPresentationName(internals, "equipment:back:token-a", true));
        var mask = profile("action.moonstation14.toggle_mask", ActionHandler.MASK_TOGGLE);
        assertEquals("action.moonstation14.toggle_mask.source.mask.raised",
                BodyActionService.providerPresentationName(mask, "equipment:mask:token-d", true),
                "low-pressure decoration applies only to internals actions");
        assertEquals("action.moonstation14.toggle_mask.source.mask.raised",
                BodyActionService.providerPresentationName(mask, "equipment:mask:token-d", false, false));
        assertEquals("action.moonstation14.toggle_mask.source.mask.lowered",
                BodyActionService.providerPresentationName(mask, "equipment:mask:token-d", false, true));
        assertEquals("action.moonstation14.toggle_internals.source.back.low_pressure",
                BodyActionService.providerPresentationName(internals, "equipment:back:token-a", true, true));
    }
}
