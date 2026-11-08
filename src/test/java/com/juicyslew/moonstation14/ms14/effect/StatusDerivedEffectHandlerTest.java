package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusDerivedEffectHandlerTest {
    @Test
    void statusDurationsUseCeilingAndDelayConversionDoesNotScale() {
        assertEquals(OptionalInt.of(2), EffectHandlers.scaledDuration(.0501f, 1f).ticks());
        assertEquals(OptionalInt.of(3), EffectHandlers.scaledDuration(.1001f, 1.25f).ticks());
        assertEquals(0, StatusEffectSystem.nonNegativeSecondsToTicks(0f));
        assertEquals(6, StatusEffectSystem.nonNegativeSecondsToTicks(.251f));
        assertEquals(267, StatusEffectSystem.nonNegativeSecondsToTicks(80f / 6f),
                "upstream vomit slowdown uses ceil(80/6 seconds * 20 ticks)");
        assertThrows(IllegalArgumentException.class,
                () -> EffectHandlers.scaledDuration(1f, -1f));
        assertThrows(IllegalArgumentException.class,
                () -> EffectHandlers.scaledDuration(Float.MAX_VALUE, 2f));
    }

    @Test
    void exactZeroIsAnExplicitNoMutationConversion() {
        EffectHandlers.DurationConversion conversion = EffectHandlers.scaledDuration(4f, 0f);
        assertTrue(conversion.zero());
        assertTrue(conversion.ticks().isEmpty());
    }

    @Test
    void genericAdapterMapsExplicitAliasesAndDeniesUnknownAliases() {
        List<String> warnings = new ArrayList<>();
        GenericStatusEffectAdapter adapter = new GenericStatusEffectAdapter(warnings::add);

        assertEquals(ModStatusEffects.createKey("jitter"),
                adapter.resolveAlias("JITTER", "Jittering").orElseThrow());
        assertEquals(ModStatusEffects.createKey("jitter"),
                adapter.resolveAlias("jitter", "").orElseThrow());
        assertEquals(ModStatusEffects.createKey("muted"),
                adapter.resolveAlias("MUTED", "muted").orElseThrow());
        assertEquals(ModStatusEffects.createKey("muted"),
                adapter.resolveAlias("muted", "").orElseThrow());
        assertTrue(adapter.resolveAlias("pressureimmunity", "pressureimmunity").isEmpty());
        assertTrue(adapter.resolveAlias("PRESSUREIMMUNITY", "PRESSUREIMMUNITY").isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void mutedGenericDurationUsesScaleAndRepeatedAddAccumulates() {
        var duration = EffectHandlers.scaledDuration(10f, 1f);
        var scaled = EffectHandlers.scaledDuration(10f, 1.5f);
        assertEquals(OptionalInt.of(200), duration.ticks());
        assertEquals(OptionalInt.of(300), scaled.ticks());
        var attachment = new com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment();
        var key = ModStatusEffects.createKey("muted");
        attachment.apply(key, com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation.ADD,
                duration.ticks(), 0);
        attachment.apply(key, com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation.ADD,
                scaled.ticks(), 0);
        assertEquals(OptionalInt.of(500), attachment.get(key).orElseThrow().remainingDurationTicks());
    }
}
