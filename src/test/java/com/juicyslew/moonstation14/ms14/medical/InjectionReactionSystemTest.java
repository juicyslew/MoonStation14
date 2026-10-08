package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InjectionReactionSystemTest {
    @Test
    void injectionMethodUsesCanonicalCodecWithoutChangingTouch() {
        assertEquals(ReactiveComponent.ReactiveMethod.INJECTION,
                ReactiveComponent.ReactiveMethod.CODEC.parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString("\"injection\""))
                        .getOrThrow());
        assertEquals("injection", ReactiveComponent.ReactiveMethod.CODEC.encodeStart(JsonOps.INSTANCE,
                ReactiveComponent.ReactiveMethod.INJECTION).getOrThrow().getAsString());
        assertEquals(ReactiveComponent.ReactiveMethod.TOUCH,
                ReactiveComponent.ReactiveMethod.CODEC.parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseString("\"touch\"")).getOrThrow());
        assertTrue(ReactiveComponent.ReactiveMethod.CODEC.parse(JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("\"Injection\"")).error().isPresent());
    }

    @Test
    void admissionRequiresInjectionAndMatchingTargetGroup() {
        var flammableInjection = new ReactiveComponent(List.of(ReactiveComponent.ReactiveGroup.FLAMMABLE),
                List.of(ReactiveComponent.ReactiveMethod.INJECTION));
        var acidicInjection = new ReactiveComponent(List.of(ReactiveComponent.ReactiveGroup.ACIDIC),
                List.of(ReactiveComponent.ReactiveMethod.INJECTION));
        var flammableTouch = new ReactiveComponent(List.of(ReactiveComponent.ReactiveGroup.FLAMMABLE),
                List.of(ReactiveComponent.ReactiveMethod.TOUCH));

        assertTrue(InjectionReactionSystem.admitsInjection(flammableInjection,
                ReactiveComponent.ReactiveGroup.FLAMMABLE));
        assertFalse(InjectionReactionSystem.admitsInjection(acidicInjection,
                ReactiveComponent.ReactiveGroup.FLAMMABLE));
        assertFalse(InjectionReactionSystem.admitsInjection(flammableTouch,
                ReactiveComponent.ReactiveGroup.FLAMMABLE));
        assertFalse(InjectionReactionSystem.admitsInjection(null,
                ReactiveComponent.ReactiveGroup.FLAMMABLE));
    }
}
