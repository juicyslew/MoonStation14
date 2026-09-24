package com.juicyslew.moonstation14.ms14.effect;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismReport;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjustReagentEffectHandlerTest {
    private static final ResourceKey<ReagentData> REAGENT = ModReagents.createKey("adjust-reagent");
    private static final ResourceKey<ReagentData> OTHER = ModReagents.createKey("other-reagent");
    private static final ResourceKey<ReagentData> THIRD = ModReagents.createKey("third-reagent");

    @Test
    void positiveAddUsesTheRoutedSolutionAndClampsToCapacity() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(OTHER, 2f));

        assertEquals(EffectResult.APPLIED, apply(1.5f, 1f, solution, 3f));
        assertEquals(Map.of(OTHER, 2f, REAGENT, 1f), solution.getMap());

        assertEquals(EffectResult.APPLIED, apply(2f, 1f, solution, 3f));
        assertEquals(Map.of(OTHER, 2f, REAGENT, 1f), solution.getMap());
    }

    @Test
    void negativeDeltaRemovesOnlyTheNamedReagentAndClampsAtZero() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 2f, OTHER, 4f));

        assertEquals(EffectResult.APPLIED, apply(-.75f, 2f, solution, 10f));
        assertEquals(Map.of(REAGENT, .5f, OTHER, 4f), solution.getMap());

        assertEquals(EffectResult.APPLIED, apply(-5f, 1f, solution, 10f));
        assertEquals(Map.of(OTHER, 4f), solution.getMap());

        assertEquals(EffectResult.APPLIED, apply(-1f, 1f, solution, 10f));
        assertEquals(Map.of(OTHER, 4f), solution.getMap());
    }

    @Test
    void zeroAndScaledDeltasAreSafe() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 1f));

        assertEquals(EffectResult.APPLIED, apply(0f, 1f, solution, 10f));
        assertEquals(Map.of(REAGENT, 1f), solution.getMap());
        assertEquals(EffectResult.APPLIED, apply(2f, .25f, solution, 10f));
        assertEquals(Map.of(REAGENT, 1.5f), solution.getMap());
        assertEquals(EffectResult.APPLIED, apply(-2f, .25f, solution, 10f));
        assertEquals(Map.of(REAGENT, 1f), solution.getMap());
    }

    @Test
    void missingRoutingIsUnsupportedAndDoesNotTouchAnySolution() {
        ReagentAttachment unrelatedTarget = new ReagentAttachment(Map.of(REAGENT, 4f));
        EffectData.AdjustReagent effect = new EffectData.AdjustReagent(
                EffectCommonData.DEFAULT, REAGENT, 3f);

        assertEquals(EffectResult.SKIPPED_UNSUPPORTED,
                EffectHandlers.adjustReagent(effect, 1f, Optional.empty()));
        assertEquals(Map.of(REAGENT, 4f), unrelatedTarget.getMap());
    }

    @Test
    void invalidArithmeticCannotCreateNanOrNegativeState() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 1f));
        EffectData.AdjustReagent effect = new EffectData.AdjustReagent(
                EffectCommonData.DEFAULT, REAGENT, Float.NaN);

        assertEquals(EffectResult.FAILED, apply(effect, 1f, solution, 10f));
        assertTrue(solution.getMap().values().stream().allMatch(value ->
                Float.isFinite(value) && value >= 0f));
        assertEquals(Map.of(REAGENT, 1f), solution.getMap());
    }

    @Test
    void metabolismReservesOrdinaryRemovalCapacityForPositiveAdjustments() {
        ReagentAttachment full = new ReagentAttachment(Map.of(REAGENT, 10f));
        MetabolismReport fullReport = processWithEffects(full, 10f,
                List.of(new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 1f)));
        assertEquals(1f, fullReport.attempts().get(0).actualRemoved());
        assertEquals(9f, total(full));
        assertEquals(9f, full.getMap().get(REAGENT));

        ReagentAttachment partial = new ReagentAttachment(Map.of(REAGENT, 8f));
        MetabolismReport partialReport = processWithEffects(partial, 10f,
                List.of(new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 1f)));
        assertEquals(1f, partialReport.attempts().get(0).actualRemoved());
        assertEquals(8f, total(partial));
        assertEquals(7f, partial.getMap().get(REAGENT));
        assertEquals(1f, partial.getMap().get(OTHER));
    }

    @Test
    void negativeAdjustmentsFreeOnlyTheirOwnRoomForLaterPositiveAdjustments() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 10f));
        MetabolismReport report = processWithEffects(solution, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -1f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 2f)));

        assertEquals(1f, report.attempts().get(0).actualRemoved());
        assertEquals(8f, solution.getMap().get(REAGENT));
        assertEquals(1f, solution.getMap().get(OTHER));
        assertEquals(9f, total(solution));
    }

    @Test
    void exhaustedLiveMetabolizedReagentReleasesItsReservationForLaterPositiveAdjustment() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 1f));
        MetabolismReport report = processWithEffects(solution, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -1f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 10f)));

        assertEquals(1f, report.attempts().get(0).actualRemoved());
        assertEquals(10f, solution.getMap().get(OTHER));
        assertEquals(10f, total(solution));
    }

    @Test
    void sameReagentPositiveAdjustmentReplenishesReleasedReservation() {
        ReagentAttachment partial = new ReagentAttachment(Map.of(REAGENT, 1f));
        processWithEffects(partial, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -.5f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 1f)));
        assertEquals(.5f, partial.getMap().get(REAGENT));

        ReagentAttachment exhausted = new ReagentAttachment(Map.of(REAGENT, 1f));
        processWithEffects(exhausted, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -1f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 10f)));
        assertEquals(9f, exhausted.getMap().get(REAGENT));
    }

    @Test
    void partialSameReagentRestorationCanConsumeOnlyReservation() {
        ReagentAttachment solution = new ReagentAttachment();
        ReagentEffectContext context = context(solution, 10f, 1f);

        assertEquals(EffectResult.APPLIED, EffectHandlers.adjustReagent(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -.5f),
                1f, Optional.of(context)));
        assertEquals(EffectResult.APPLIED, EffectHandlers.adjustReagent(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, .25f),
                1f, Optional.of(context)));

        assertTrue(solution.isEmpty());
        assertEquals(.75f, context.transactionState().remainingReservation());
        assertEquals(.75f,
                com.juicyslew.moonstation14.eventhooks.TickHooks.metabolismConditionContext(
                        context.sourceSnapshot(), Map.of(), solution.getMap(), context.transactionState())
                        .sourceReagentQuantities().orElseThrow().get(REAGENT));
    }

    @Test
    void liveQuantityIsRemovedBeforeReservationForPartiallyExhaustingAdjustment() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 3f));
        MetabolismReport report = processWithEffects(solution, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -2f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 10f)));

        assertEquals(1f, report.attempts().get(0).actualRemoved());
        assertEquals(0f, solution.getMap().getOrDefault(REAGENT, 0f));
        assertEquals(9f, solution.getMap().get(OTHER));
        assertEquals(9f, total(solution));
    }

    @Test
    void multiplePositiveAdjustmentsShareReservedCapacity() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 8f));
        processWithEffects(solution, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 1f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, THIRD, 10f)));

        assertEquals(1f, solution.getMap().get(OTHER));
        assertEquals(1f, solution.getMap().get(THIRD));
        assertEquals(9f, total(solution));
    }

    @Test
    void anotherReagentRemovalFreesPhysicalRoomWithoutConsumingReservation() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 9f, OTHER, 1f));
        processWithEffects(solution, 10f, List.of(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, -1f),
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, THIRD, 10f)));

        assertEquals(8f, solution.getMap().get(REAGENT));
        assertEquals(1f, solution.getMap().get(THIRD));
        assertEquals(9f, total(solution));
    }

    @Test
    void productsStillUsePhysicalRoomFreedByOrdinaryRemoval() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 10f));
        MetabolismReport report = processWithEffects(solution, 10f,
                List.of(new EffectData.AdjustReagent(EffectCommonData.DEFAULT, OTHER, 1f)),
                Map.of(OTHER, 1f), 1f);

        assertEquals(1f, report.attempts().get(0).actualRemoved());
        assertEquals(9f, solution.getMap().get(REAGENT));
        assertEquals(1f, solution.getMap().get(OTHER));
        assertEquals(10f, total(solution));
    }

    @Test
    void sameReagentRestorationAndMetaboliteProductionRetainBothAccounts() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 1f));
        MetabolismReport report = processWithEffects(solution, 10f, List.of(
                        new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, -1f),
                        new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 10f)),
                Map.of(OTHER, 1f), 1f);

        assertEquals(1f, report.attempts().get(0).actualRemoved());
        assertEquals(9f, solution.getMap().get(REAGENT));
        assertEquals(1f, solution.getMap().get(OTHER));
        assertEquals(10f, total(solution));
    }

    @Test
    void invalidPhysicalStateOrCapacityLeavesReservationAndSolutionUnchanged() {
        ReagentAttachment invalidCapacity = new ReagentAttachment();
        ReagentEffectContext capacityContext = context(invalidCapacity, Float.NaN, 1f);
        assertEquals(EffectResult.FAILED, EffectHandlers.adjustReagent(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 1f),
                1f, Optional.of(capacityContext)));
        assertTrue(invalidCapacity.isEmpty());
        assertEquals(1f, capacityContext.transactionState().remainingReservation());

        ReagentAttachment invalidSolution = new ReagentAttachment(Map.of(OTHER, 11f));
        ReagentEffectContext solutionContext = context(invalidSolution, 10f, 1f);
        assertEquals(EffectResult.FAILED, EffectHandlers.adjustReagent(
                new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 1f),
                1f, Optional.of(solutionContext)));
        assertEquals(Map.of(OTHER, 11f), invalidSolution.getMap());
        assertEquals(1f, solutionContext.transactionState().remainingReservation());
    }

    @Test
    void adjustingTheMetabolizedReagentDoesNotChangeRemovalAccounting() {
        ReagentAttachment solution = new ReagentAttachment(Map.of(REAGENT, 5f));
        MetabolismReport report = processWithEffects(solution, 10f,
                List.of(new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, 3f)), 2f);

        assertEquals(2f, report.attempts().get(0).actualRemoved());
        assertEquals(6f, solution.getMap().get(REAGENT));
    }

    @Test
    void invalidReservationOrCapacityFailsWithoutMutatingTheTransaction() {
        ReagentAttachment reservationInvalid = new ReagentAttachment(Map.of(REAGENT, 1f));
        assertEquals(EffectResult.FAILED, apply(1f, 1f, reservationInvalid, 10f, 11f));
        assertEquals(Map.of(REAGENT, 1f), reservationInvalid.getMap());

        ReagentAttachment capacityInvalid = new ReagentAttachment(Map.of(REAGENT, 1f));
        assertEquals(EffectResult.FAILED, apply(1f, 1f, capacityInvalid, Float.NaN, 0f));
        assertEquals(Map.of(REAGENT, 1f), capacityInvalid.getMap());

        ReagentAttachment arithmeticOverflow = new ReagentAttachment(Map.of(REAGENT, 1f));
        assertEquals(EffectResult.FAILED,
                apply(Float.MAX_VALUE, 2f, arithmeticOverflow, 10f, 0f));
        assertEquals(Map.of(REAGENT, 1f), arithmeticOverflow.getMap());
    }

    private static EffectResult apply(float amount, float scale,
                                      ReagentAttachment solution, float capacity) {
        return apply(amount, scale, solution, capacity, 0f);
    }

    private static EffectResult apply(float amount, float scale,
                                      ReagentAttachment solution, float capacity,
                                      float ordinaryRemoved) {
        return apply(new EffectData.AdjustReagent(EffectCommonData.DEFAULT, REAGENT, amount),
                scale, solution, capacity, ordinaryRemoved);
    }

    private static EffectResult apply(EffectData.AdjustReagent effect, float scale,
                                      ReagentAttachment solution, float capacity) {
        return apply(effect, scale, solution, capacity, 0f);
    }

    private static EffectResult apply(EffectData.AdjustReagent effect, float scale,
                                      ReagentAttachment solution, float capacity,
                                      float ordinaryRemoved) {
        return EffectHandlers.adjustReagent(effect, scale,
                Optional.of(context(solution, capacity, ordinaryRemoved)));
    }

    private static ReagentEffectContext context(ReagentAttachment solution, float capacity,
                                                 float ordinaryRemoved) {
        IReagentTrait trait = () -> capacity;
        TraitHandler<IReagentTrait> source = new TraitHandler<>(new Object(), trait);
        ReagentData prototype = ReagentData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"adjust-reagent\"}")).getOrThrow();
        MetabolismSystem.SourceSnapshot snapshot = new MetabolismSystem.SourceSnapshot(
                REAGENT, prototype, 1f, solution.getMap());
        return new ReagentEffectContext(source, solution, REAGENT, MetabolismStage.BLOODSTREAM,
                MetabolizerProfile.SHARED_LIVING, snapshot, ordinaryRemoved,
                new ReagentEffectTransactionState(REAGENT, ordinaryRemoved), Optional.empty());
    }

    private static MetabolismReport processWithEffects(ReagentAttachment solution, float capacity,
                                                        List<EffectData> effects) {
        return processWithEffects(solution, capacity, effects, Map.of(), 1f);
    }

    private static MetabolismReport processWithEffects(ReagentAttachment solution, float capacity,
                                                        List<EffectData> effects, float rate) {
        return processWithEffects(solution, capacity, effects, Map.of(), rate);
    }

    private static MetabolismReport processWithEffects(ReagentAttachment solution, float capacity,
                                                        List<EffectData> effects,
                                                        Map<ResourceKey<ReagentData>, Float> metabolites,
                                                        float rate) {
        ResourceLocation location = REAGENT.location();
        ReagentData source = new ReagentData("adjust-source", "adjust-source", "test", "", "", "",
                0, Map.of(), 400f, 200f, com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum.NONE,
                List.of(), Map.of(MetabolismStage.RESPIRATION,
                        new MetabolismData(effects, metabolites, rate)),
                new com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData(null, null),
                0f, "fill-", false, 0f);
        IReagentTrait trait = () -> capacity;
        TraitHandler<IReagentTrait> sourceHandle = new TraitHandler<>(new Object(), trait);
        return MetabolismSystem.process(solution, Map.of(location, source),
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1), capacity,
                RandomSource.create(1L), invocation -> {
                    ReagentEffectContext context = new ReagentEffectContext(sourceHandle, solution,
                            invocation.reagent(), invocation.stage(), MetabolizerProfile.SHARED_LIVING,
                            invocation.sourceSnapshot(), invocation.actualRemoved(),
                            new ReagentEffectTransactionState(invocation.reagent(), invocation.actualRemoved()),
                            Optional.empty());
                    for (EffectData effect : invocation.metabolism().effects()) {
                        assertEquals(EffectResult.APPLIED,
                                EffectHandlers.adjustReagent((EffectData.AdjustReagent) effect,
                                        invocation.scale(), Optional.of(context)));
                    }
                });
    }

    private static float total(ReagentAttachment solution) {
        return solution.getMap().values().stream().reduce(0f, Float::sum);
    }
}
