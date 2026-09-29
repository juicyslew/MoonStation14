package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectKernelTest {
    @Test
    void scaleGatePrecedesProbabilityAndConditions() {
        EffectData.Jitter effect = new EffectData.Jitter(
                new EffectCommonData(List.of(
                        new com.juicyslew.moonstation14.component.codec.json.ConditionData.BreathingCondition()),
                        0f, 2f, true));
        RandomSource random = RandomSource.create(1234L);
        RandomSource control = RandomSource.create(1234L);

        EffectSystem.GateDecision decision = EffectSystem.evaluateGates(
                effect, 1f, random, ConditionContext.unavailable());

        assertEquals(EffectResult.SKIPPED_SCALE, decision.result());
        assertFalse(decision.randomConsumed());
        assertEquals(control.nextFloat(), random.nextFloat());
    }

    @Test
    void probabilityIsIndependentOfScaleAndConsumesExactlyOneRoll() {
        EffectData.Jitter always = new EffectData.Jitter(
                new EffectCommonData(List.of(), 1f, 0f, true));
        EffectData.Jitter never = new EffectData.Jitter(
                new EffectCommonData(List.of(), 0f, 0f, true));
        RandomSource lowScaleRandom = RandomSource.create(5678L);
        RandomSource highScaleRandom = RandomSource.create(5678L);
        RandomSource control = RandomSource.create(5678L);

        EffectSystem.GateDecision admitted = EffectSystem.evaluateGates(
                always, .1f, lowScaleRandom, ConditionContext.unavailable());
        EffectSystem.GateDecision rejected = EffectSystem.evaluateGates(
                never, 10f, highScaleRandom, ConditionContext.unavailable());

        assertEquals(EffectResult.APPLIED, admitted.result());
        assertEquals(.1f, admitted.scale());
        assertTrue(admitted.randomConsumed());
        assertEquals(EffectResult.SKIPPED_PROBABILITY, rejected.result());
        assertTrue(rejected.randomConsumed());
        control.nextFloat();
        float expectedNext = control.nextFloat();
        assertEquals(expectedNext, lowScaleRandom.nextFloat());
        assertEquals(expectedNext, highScaleRandom.nextFloat());
    }

    @Test
    void probabilityAboveOnePassesWhileConsumingExactlyOneRoll() {
        EffectData.Jitter effect = new EffectData.Jitter(
                new EffectCommonData(List.of(), 1.1f, 0f, true));
        RandomSource random = RandomSource.create(5678L);
        RandomSource control = RandomSource.create(5678L);

        EffectSystem.GateDecision decision = EffectSystem.evaluateGates(
                effect, .1f, random, ConditionContext.unavailable());

        assertEquals(EffectResult.APPLIED, decision.result());
        assertTrue(decision.randomConsumed());
        control.nextFloat();
        assertEquals(control.nextFloat(), random.nextFloat());
    }

    @Test
    void probabilityOnePassesWhileConsumingExactlyOneRoll() {
        EffectData.Jitter effect = new EffectData.Jitter(
                new EffectCommonData(List.of(), 1f, 0f, true));
        RandomSource random = RandomSource.create(5678L);
        RandomSource control = RandomSource.create(5678L);

        EffectSystem.GateDecision decision = EffectSystem.evaluateGates(
                effect, .1f, random, ConditionContext.unavailable());

        assertEquals(EffectResult.APPLIED, decision.result());
        assertTrue(decision.randomConsumed());
        control.nextFloat();
        assertEquals(control.nextFloat(), random.nextFloat());
    }

    @Test
    void lowScaleDoesNotMultiplyProbabilityThreshold() {
        EffectData.Jitter effect = new EffectData.Jitter(
                new EffectCommonData(List.of(), .1f, 0f, true));
        RandomSource random = RandomSource.create(4096L);
        RandomSource control = RandomSource.create(4096L);
        float sample = control.nextFloat();

        assertTrue(sample > .01f && sample < .1f);
        EffectSystem.GateDecision decision = EffectSystem.evaluateGates(
                effect, .1f, random, ConditionContext.unavailable());

        assertEquals(EffectResult.APPLIED, decision.result());
        assertTrue(decision.randomConsumed());
        assertEquals(control.nextFloat(), random.nextFloat());
    }

    @Test
    void scalingFalseCapsOnlyAfterConditionsAndDoesNotRaisePartialScale() {
        EffectData.Jitter effect = new EffectData.Jitter(
                new EffectCommonData(List.of(), 1f, 2f, false));

        EffectSystem.GateDecision admitted = EffectSystem.evaluateGates(
                effect, 3f, RandomSource.create(), ConditionContext.unavailable());
        assertEquals(EffectResult.APPLIED, admitted.result());
        assertEquals(1f, admitted.scale());

        EffectData.Jitter partial = new EffectData.Jitter(
                new EffectCommonData(List.of(), 1f, 0f, false));
        assertEquals(.25f, EffectSystem.evaluateGates(partial, .25f,
                RandomSource.create(), ConditionContext.unavailable()).scale());
    }

    @Test
    void minimumScaleIsInclusiveAndConditionsRunBeforeScaleAdjustment() {
        EffectData.Jitter atMinimum = new EffectData.Jitter(
                new EffectCommonData(List.of(), 2f, 2f, false));
        assertEquals(EffectResult.APPLIED, EffectSystem.evaluateGates(atMinimum, 2f,
                RandomSource.create(), ConditionContext.unavailable()).result());

        EffectData.Jitter conditionallyRejected = new EffectData.Jitter(
                new EffectCommonData(List.of(new com.juicyslew.moonstation14.component.codec.json.ConditionData.BreathingCondition()),
                        1f, 0f, false));
        assertEquals(EffectResult.SKIPPED_CONDITION, EffectSystem.evaluateGates(conditionallyRejected, 2f,
                RandomSource.create(), ConditionContext.unavailable()).result());

        EffectData.Jitter probabilityRejected = new EffectData.Jitter(
                new EffectCommonData(conditionallyRejected.conditions(), 0f, 0f, false));
        assertEquals(EffectResult.SKIPPED_PROBABILITY, EffectSystem.evaluateGates(
                probabilityRejected, 2f, RandomSource.create(), ConditionContext.unavailable()).result());
    }

    @Test
    void dispatcherRejectsDuplicatesAndDispatchesByExactGenericType() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        dispatcher.register(EffectData.Jitter.class, (effect, context) -> EffectResult.APPLIED);
        assertThrows(IllegalArgumentException.class,
                () -> dispatcher.register(EffectData.Jitter.class, (effect, context) -> EffectResult.APPLIED));
    }

    @Test
    void dispatcherWarnsOncePerUnsupportedType() {
        List<String> warnings = new ArrayList<>();
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> warnings.add(effect.type()));

        EffectData.Drunk unsupported = new EffectData.Drunk(EffectCommonData.DEFAULT, 1f);
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, dispatcher.dispatch(unsupported, null));
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, dispatcher.dispatch(unsupported, null));
        assertEquals(EffectResult.SKIPPED_UNSUPPORTED, dispatcher.dispatch(
                new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f), null));
        assertEquals(List.of("Drunk", "ModifyBleed"), warnings);
    }

    @Test
    void defaultsRegisterExistingBehaviorsAndAdjustReagent() {
        EffectDispatcher dispatcher = new EffectDispatcher(effect -> { });
        EffectHandlers.registerDefaults(dispatcher);

        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.EvenHealthChange.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.HealthChange.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.Vomit.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.Jitter.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.AdjustReagent.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.Drunk.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.ModifyStatusEffect.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.GenericStatusEffect.class, (effect, context) -> EffectResult.APPLIED));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(
                EffectData.PopupMessage.class, (effect, context) -> EffectResult.APPLIED));
        assertTrue(dispatcher.supportsHandler(new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f)));
        assertTrue(dispatcher.supportsHandler(new EffectData.ModifyBloodLevel(EffectCommonData.DEFAULT, 1f)));
    }

    @Test
    void applyAllKeepsOrderAndContinuesAfterFailedResult() {
        List<EffectData> effects = List.of(
                new EffectData.Jitter(EffectCommonData.DEFAULT),
                new EffectData.Drunk(EffectCommonData.DEFAULT, 1f),
                new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f));
        AtomicInteger calls = new AtomicInteger();

        List<EffectResult> results = EffectSystem.applyEach(effects, effect -> switch (calls.getAndIncrement()) {
            case 0 -> EffectResult.APPLIED;
            case 1 -> EffectResult.FAILED;
            default -> EffectResult.SKIPPED_UNSUPPORTED;
        });

        assertEquals(List.of(EffectResult.APPLIED, EffectResult.FAILED,
                EffectResult.SKIPPED_UNSUPPORTED), results);
        assertEquals(3, calls.get());
    }

    @Test
    void effectDataContainsThirtyFourDataOnlyRecordVariants() {
        List<Class<?>> variants = Arrays.stream(EffectData.class.getDeclaredClasses())
                .filter(EffectData.class::isAssignableFrom)
                .toList();

        assertEquals(34, variants.size());
        assertTrue(variants.stream().allMatch(Class::isRecord));
        assertTrue(variants.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .noneMatch(method -> method.getName().equals("apply")
                        || method.getName().equals("shouldApply")));
    }

    @Test
    void contextAndGateDecisionAreImmutableRecords() {
        assertTrue(EffectContext.class.isRecord());
        assertTrue(EffectSystem.GateDecision.class.isRecord());
        assertEquals(List.of("level", "entity", "scale", "random", "conditionContext", "cause",
                        "reagentContext"),
                Arrays.stream(EffectContext.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
    }

    @Test
    void metabolismBuildsAFreshSourceSnapshotWithOtherCapabilitiesUnavailable() {
        var water = ModReagents.createKey("water");
        ReagentAttachment source = new ReagentAttachment(new HashMap<>(Map.of(water, 1f)));

        ConditionContext first = TickHooks.metabolismConditionContext(source);
        source.specificAdd(water, 1f, 200_000f);
        ConditionContext second = TickHooks.metabolismConditionContext(source);

        assertEquals(1f, first.sourceReagentQuantities().orElseThrow().get(water));
        assertEquals(2f, second.sourceReagentQuantities().orElseThrow().get(water));
        assertTrue(second.mobState().isEmpty());
        assertTrue(second.metabolizerTypes().isEmpty());
        assertTrue(second.temperature().isEmpty());
        assertTrue(second.hunger().isEmpty());
        assertTrue(second.breathing().isEmpty());
        assertTrue(second.internals().isEmpty());
        assertTrue(second.tags().isEmpty());
    }
}
