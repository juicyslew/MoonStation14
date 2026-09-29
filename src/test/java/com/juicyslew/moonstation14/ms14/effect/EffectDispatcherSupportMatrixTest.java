package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectDuration;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectDispatcherSupportMatrixTest {
    private static final Set<Class<? extends EffectData>> FUNCTIONAL = Set.of(
            EffectData.EvenHealthChange.class,
            EffectData.HealthChange.class,
            EffectData.Vomit.class,
            EffectData.Jitter.class,
            EffectData.MovementSpeedModifier.class,
            EffectData.Drunk.class,
            EffectData.ModifyStatusEffect.class,
            EffectData.GenericStatusEffect.class,
            EffectData.AdjustReagent.class,
            EffectData.PopupMessage.class,
            EffectData.Flammable.class,
            EffectData.Ignite.class,
            EffectData.Extinguish.class,
            EffectData.ModifyKnockdown.class,
            EffectData.EyeDamage.class,
            EffectData.Electrocute.class,
            EffectData.AdjustAlert.class,
            EffectData.AdjustTemperature.class,
            EffectData.SatiateHunger.class,
            EffectData.SatiateThirst.class,
            EffectData.Emote.class,
            EffectData.ModifyBleed.class,
            EffectData.ModifyBloodLevel.class,
            EffectData.Oxygenate.class,
            EffectData.ModifyLungGas.class);

    private static final Set<Class<? extends EffectData>> UNSUPPORTED = Set.of(
            EffectData.CleanBloodstream.class,
            EffectData.ResetNarcolepsy.class,
            EffectData.ReduceRotting.class,
            EffectData.CauseZombieInfection.class,
            EffectData.CureZombieInfection.class,
            EffectData.ArtifactDurabilityRestore.class,
            EffectData.ArtifactUnlock.class,
            EffectData.MakeSentient.class,
            EffectData.Polymorph.class);

    @Test
    void defaultsExhaustivelyClassifyExactlyTheCurrent34Variants() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        EffectHandlers.registerDefaults(dispatcher);

        Set<Class<? extends EffectData>> variants = Arrays.stream(EffectData.class.getDeclaredClasses())
                .filter(EffectData.class::isAssignableFrom)
                .map(type -> type.asSubclass(EffectData.class))
                .collect(Collectors.toSet());

        assertEquals(34, variants.size());
        assertEquals(25, dispatcher.registeredHandlerTypes().size());
        assertEquals(9, dispatcher.registeredUnsupportedTypes().size());
        assertEquals(FUNCTIONAL, dispatcher.registeredHandlerTypes());
        assertEquals(UNSUPPORTED, dispatcher.registeredUnsupportedTypes().keySet());
        assertEquals(variants, union(dispatcher.registeredHandlerTypes(),
                dispatcher.registeredUnsupportedTypes().keySet()));
        assertTrue(disjoint(dispatcher.registeredHandlerTypes(),
                dispatcher.registeredUnsupportedTypes().keySet()));
    }

    @Test
    void everyUnsupportedRegistrationHasAUsefulReason() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        EffectHandlers.registerDefaults(dispatcher);

        assertTrue(dispatcher.registeredUnsupportedTypes().values().stream()
                .allMatch(reason -> reason.trim().length() >= 12));
        assertTrue(dispatcher.registeredUnsupportedTypes().values().stream()
                .noneMatch(reason -> reason.toLowerCase().contains("todo")));
    }

    @Test
    void handlerAndUnsupportedRegistrationsRejectDuplicatesAndCrossCategoryConflicts() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        dispatcher.register(EffectData.Jitter.class, (effect, context) -> EffectResult.APPLIED);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.Jitter.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.registerUnsupported(
                EffectData.Jitter.class, "status capability unavailable"));

        dispatcher.registerUnsupported(EffectData.Drunk.class, "status capability unavailable");
        assertThrows(IllegalArgumentException.class, () -> dispatcher.registerUnsupported(
                EffectData.Drunk.class, "another reason"));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.Drunk.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.registerUnsupported(
                EffectData.HealthChange.class, " "));
    }

    @Test
    void explicitAndUnclassifiedEffectsWarnOnceWithoutAContext() {
        List<String> explicitWarnings = new ArrayList<>();
        EffectDispatcher explicit = new EffectDispatcher(effect -> explicitWarnings.add(effect.type()));
        explicit.registerUnsupported(EffectData.AdjustAlert.class, "alert capability is unavailable");
        EffectData.AdjustAlert knownUnsupported = new EffectData.AdjustAlert(
                EffectCommonData.DEFAULT, "critical", false, false, 0f);
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, explicit.dispatch(knownUnsupported, null));
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, explicit.dispatch(knownUnsupported, null));
        assertEquals(List.of("AdjustAlert"), explicitWarnings);

        List<String> unclassifiedWarnings = new ArrayList<>();
        EffectDispatcher unclassified = new EffectDispatcher(
                effect -> unclassifiedWarnings.add(effect.type()));
        EffectData.Drunk missing = new EffectData.Drunk(EffectCommonData.DEFAULT, 1f);
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, unclassified.dispatch(missing, null));
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, unclassified.dispatch(missing, null));
        assertEquals(List.of("Drunk"), unclassifiedWarnings);
    }

    @Test
    void everyExplicitlyUnsupportedEffectDispatchesWithoutAccessingContext() {
        List<String> warnings = new ArrayList<>();
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> warnings.add(effect.type()));
        EffectHandlers.registerDefaults(dispatcher);

        List<EffectData> effects = unsupportedEffects();
        assertEquals(9, effects.size());
        for (EffectData effect : effects) {
            assertTrue(UNSUPPORTED.contains(effect.getClass()), effect.type());
            assertEquals(EffectResult.SKIPPED_UNSUPPORTED, dispatcher.dispatch(effect, null));
            assertEquals(EffectResult.SKIPPED_UNSUPPORTED, dispatcher.dispatch(effect, null));
        }
        assertEquals(UNSUPPORTED.stream().map(Class::getSimpleName).collect(Collectors.toSet()),
                Set.copyOf(warnings));
        assertEquals(9, warnings.size());
    }

    @Test
    void bloodEffectsAreRegisteredAsExecutableHandlers() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        EffectHandlers.registerDefaults(dispatcher);

        assertTrue(dispatcher.supportsHandler(new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f)));
        assertTrue(dispatcher.supportsHandler(new EffectData.ModifyBloodLevel(EffectCommonData.DEFAULT, 1f)));
        assertTrue(!dispatcher.registeredUnsupportedTypes().containsKey(EffectData.ModifyBleed.class));
        assertTrue(!dispatcher.registeredUnsupportedTypes().containsKey(EffectData.ModifyBloodLevel.class));
    }

    @Test
    void lungEffectsAreRegisteredAsExecutableHandlers() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        EffectHandlers.registerDefaults(dispatcher);

        assertTrue(dispatcher.supportsHandler(new EffectData.Oxygenate(EffectCommonData.DEFAULT, 1f)));
        assertTrue(dispatcher.supportsHandler(new EffectData.ModifyLungGas(
                EffectCommonData.DEFAULT, Map.of("carbon_dioxide", 1f))));
        assertTrue(!dispatcher.registeredUnsupportedTypes().containsKey(EffectData.Oxygenate.class));
        assertTrue(!dispatcher.registeredUnsupportedTypes().containsKey(EffectData.ModifyLungGas.class));
    }

    private static List<EffectData> unsupportedEffects() {
        ResourceKey<ReagentData> water = ModReagents.createKey("water");
        return List.of(
                new EffectData.CleanBloodstream(EffectCommonData.DEFAULT, water, 1f),
                new EffectData.ResetNarcolepsy(EffectCommonData.DEFAULT),
                new EffectData.ReduceRotting(EffectCommonData.DEFAULT, 1f),
                new EffectData.CauseZombieInfection(EffectCommonData.DEFAULT),
                new EffectData.CureZombieInfection(EffectCommonData.DEFAULT, false),
                new EffectData.ArtifactDurabilityRestore(EffectCommonData.DEFAULT),
                new EffectData.ArtifactUnlock(EffectCommonData.DEFAULT),
                new EffectData.MakeSentient(EffectCommonData.DEFAULT),
                new EffectData.Polymorph(EffectCommonData.DEFAULT, "minecraft:pig"));
    }

    private static <T> Set<T> union(Set<T> left, Set<T> right) {
        java.util.HashSet<T> union = new java.util.HashSet<>(left);
        union.addAll(right);
        return union;
    }

    private static <T> boolean disjoint(Set<T> left, Set<T> right) {
        return left.stream().noneMatch(right::contains);
    }
}
