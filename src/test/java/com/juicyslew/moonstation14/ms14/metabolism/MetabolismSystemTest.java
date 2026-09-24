package com.juicyslew.moonstation14.ms14.metabolism;

import com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData;
import com.juicyslew.moonstation14.component.codec.json.MetabolismData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetabolismSystemTest {
    private static final ResourceKey<ReagentData> A = ModReagents.createKey("kernel-a");
    private static final ResourceKey<ReagentData> B = ModReagents.createKey("kernel-b");
    private static final ResourceKey<ReagentData> C = ModReagents.createKey("kernel-c");

    @Test
    void processesPartialAndFullRemovalWithScaledProductionAndCapacityAccounting() {
        ReagentData source = reagent("a", MetabolismStage.RESPIRATION,
                new MetabolismData(List.of(), Map.of(B, 2f), 2f));
        Map<ResourceLocation, ReagentData> catalog = Map.of(A.location(), source);

        ReagentAttachment partial = new ReagentAttachment(Map.of(A, 1f));
        MetabolismReport.MetabolismAttempt partialAttempt = run(partial, catalog, 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1)).attempts().get(0);
        assertEquals(1f, partialAttempt.actualRemoved());
        assertEquals(.5f, partialAttempt.scale());
        assertEquals(2f, partialAttempt.requestedProduction().get(B));
        assertEquals(2f, partialAttempt.retained().get(B));
        assertEquals(0f, partialAttempt.excess().get(B));

        ReagentAttachment full = new ReagentAttachment(Map.of(A, 3f));
        MetabolismReport.MetabolismAttempt fullAttempt = run(full, catalog, 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1)).attempts().get(0);
        assertEquals(2f, fullAttempt.actualRemoved());
        assertEquals(1f, fullAttempt.scale());
        assertEquals(4f, fullAttempt.requestedProduction().get(B));
        assertEquals(4f, full.getMap().get(B));

        assertTrue(run(new ReagentAttachment(Map.of(A, 0f)), catalog, 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1)).attempts().isEmpty());
    }

    @Test
    void metabolismCanConsumeFromStomachAndRouteProductsToBodyWithoutMixingSources() {
        ReagentData source = reagent("a", MetabolismStage.DIGESTION,
                new MetabolismData(List.of(), Map.of(B, 2f), 2f));
        ReagentAttachment stomach = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment body = new ReagentAttachment(Map.of(C, 3f));

        MetabolismReport report = MetabolismSystem.process(stomach, body,
                Map.of(A.location(), source), MetabolizerProfile.STOMACH, 10f,
                RandomSource.create(5L), invocation -> { });

        assertEquals(1, report.attempts().size());
        assertEquals(MetabolismStage.DIGESTION, report.attempts().get(0).stage());
        assertTrue(stomach.isEmpty());
        assertEquals(Map.of(C, 3f, B, 2f), body.getMap());
    }

    @Test
    void routedProcessingUsesIndependentSourceAndDestinationCapacities() {
        ReagentData source = reagent("a", MetabolismStage.DIGESTION,
                new MetabolismData(List.of(), Map.of(B, 1f), 4f));
        ReagentAttachment stomach = new ReagentAttachment(Map.of(A, 50f));
        ReagentAttachment body = new ReagentAttachment(Map.of(C, 999f));
        MetabolismReport report = MetabolismSystem.process(stomach, body,
                Map.of(A.location(), source), MetabolizerProfile.STOMACH, 50f, 1000f,
                RandomSource.create(5L), invocation -> { });
        var attempt = report.attempts().get(0);
        assertEquals(4f, attempt.actualRemoved());
        assertEquals(4f, attempt.requestedProduction().get(B));
        assertEquals(1f, attempt.retained().get(B));
        assertEquals(3f, attempt.excess().get(B));
        assertEquals(100L, attempt.exactCapacity().retained().get(B));
        assertEquals(300L, attempt.exactCapacity().excess().get(B));
        assertEquals(1000f, body.getMap().values().stream().mapToDouble(Float::doubleValue).sum());
        assertEquals(46f, stomach.getMap().get(A));
    }

    @Test
    void routedProcessingRejectsEitherOverCapacityAttachmentBeforeRemoval() {
        ReagentData source = reagent("a", MetabolismStage.DIGESTION,
                new MetabolismData(List.of(), Map.of(B, 1f), 1f));
        Map<ResourceLocation, ReagentData> catalog = Map.of(A.location(), source);
        ReagentAttachment validStomach = new ReagentAttachment(Map.of(A, 1f));
        ReagentAttachment overfullBody = new ReagentAttachment(Map.of(C, 1001f));
        assertThrows(IllegalArgumentException.class, () -> MetabolismSystem.process(validStomach, overfullBody,
                catalog, MetabolizerProfile.STOMACH, 50f, 1000f, RandomSource.create(), invocation -> { }));
        assertEquals(1f, validStomach.getMap().get(A));
        ReagentAttachment overfullStomach = new ReagentAttachment(Map.of(A, 51f));
        assertThrows(IllegalArgumentException.class, () -> MetabolismSystem.process(overfullStomach,
                new ReagentAttachment(), catalog, MetabolizerProfile.STOMACH, 50f, 1000f,
                RandomSource.create(), invocation -> { }));
        assertEquals(51f, overfullStomach.getMap().get(A));
    }

    @Test
    void capResetsPerStageAndSkippedEntriesDoNotConsumeIt() {
        Map<ResourceLocation, ReagentData> catalog = new LinkedHashMap<>();
        catalog.put(A.location(), reagent("a", MetabolismStage.DIGESTION,
                new MetabolismData(List.of(), Map.of(), 1f)));
        catalog.put(B.location(), reagent("b", MetabolismStage.RESPIRATION,
                new MetabolismData(List.of(), Map.of(), 1f)));
        MetabolizerProfile profile = new MetabolizerProfile(
                List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION), 1);
        MetabolismReport report = run(new ReagentAttachment(Map.of(A, 1f, B, 1f)), catalog, 10f, profile);
        assertEquals(2, report.attempts().size());
        assertEquals(MetabolismStage.RESPIRATION, report.attempts().get(0).stage());
        assertEquals(MetabolismStage.DIGESTION, report.attempts().get(1).stage());
    }

    @Test
    void sortedShuffleIsIndependentOfInsertionOrderAndEqualSeedsMatch() {
        List<ResourceKey<ReagentData>> keys = canonicalSortKeys();
        Map<ResourceKey<ReagentData>, Float> firstContents = new LinkedHashMap<>();
        Map<ResourceKey<ReagentData>, Float> secondContents = new LinkedHashMap<>();
        Map<ResourceLocation, ReagentData> catalog = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            ResourceKey<ReagentData> key = keys.get(i);
            firstContents.put(key, 1f);
            secondContents.put(key, 1f);
            catalog.put(key.location(), reagent("collision-" + i,
                    MetabolismStage.RESPIRATION, new MetabolismData(List.of(), Map.of(), 1f)));
        }
        secondContents = reverse(secondContents);

        MetabolizerProfile profile = new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), keys.size());
        List<ResourceKey<ReagentData>> first = order(new ReagentAttachment(firstContents), catalog, 20f, profile,
                RandomSource.create(42L));
        List<ResourceKey<ReagentData>> second = order(new ReagentAttachment(secondContents), catalog, 20f, profile,
                RandomSource.create(42L));
        assertEquals(first, second);

        // Canonical sorting gives a,b,c,d; zero swaps in the injected Fisher-Yates source
        // make the resulting rotation observable without depending on map iteration.
        List<ResourceKey<ReagentData>> expectedWithFixedRandom = List.of(
                ModReagents.createKey("kernel-sort-b"),
                ModReagents.createKey("kernel-sort-c"),
                ModReagents.createKey("kernel-sort-d"),
                ModReagents.createKey("kernel-sort-a"));
        assertEquals(expectedWithFixedRandom,
                order(new ReagentAttachment(firstContents), catalog, 20f, profile, new FixedRandomSource(0)));
        assertEquals(expectedWithFixedRandom,
                order(new ReagentAttachment(secondContents), catalog, 20f, profile, new FixedRandomSource(0)));
    }

    @Test
    void generatedReagentIsNotACurrentStageCandidateButIsProcessedLater() {
        Map<ResourceLocation, ReagentData> catalog = Map.of(
                A.location(), reagent("a", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(B, 1f), 1f)),
                B.location(), reagent("b",
                        MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(C, 1f), 1f),
                        MetabolismStage.DIGESTION,
                        new MetabolismData(List.of(), Map.of(C, 1f), 1f)),
                C.location(), reagent("c"));
        MetabolismReport report = run(new ReagentAttachment(Map.of(A, 1f)), catalog, 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION), 2));
        assertEquals(List.of(A, B), report.attempts().stream()
                .map(MetabolismReport.MetabolismAttempt::reagent).toList());
        assertEquals(List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION), report.attempts().stream()
                .map(MetabolismReport.MetabolismAttempt::stage).toList());
        assertEquals(1f, report.attempts().get(1).sourceSnapshot().amount());
        assertEquals(1f, report.attempts().get(1).actualRemoved());
        assertEquals(1f, report.attempts().get(1).retained().get(C));
    }

    @Test
    void zeroValuedStoredReagentIsNotAddedToCurrentStageCandidates() {
        Map<ResourceLocation, ReagentData> catalog = Map.of(
                A.location(), reagent("a", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(B, 1f), 1f)),
                B.location(), reagent("b",
                        MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 1f),
                        MetabolismStage.DIGESTION,
                        new MetabolismData(List.of(), Map.of(), 1f)));

        MetabolismReport report = run(new ReagentAttachment(Map.of(A, 1f, B, 0f)), catalog, 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION), 2));

        assertEquals(List.of(A, B), report.attempts().stream()
                .map(MetabolismReport.MetabolismAttempt::reagent).toList());
        assertEquals(List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION), report.attempts().stream()
                .map(MetabolismReport.MetabolismAttempt::stage).toList());
    }

    @Test
    void liveQuantityIsRereadForAnAlreadySnapshottedCandidate() {
        Map<ResourceLocation, ReagentData> catalog = Map.of(
                A.location(), reagent("a", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 1f)),
                B.location(), reagent("b", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 10f)));
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f, B, 1f));
        List<ResourceKey<ReagentData>> order = new ArrayList<>();

        MetabolismReport report = MetabolismSystem.process(attachment, catalog,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 2), 20f,
                new FixedRandomSource(1), invocation -> {
                    order.add(invocation.reagent());
                    if (invocation.reagent().equals(A)) {
                        attachment.specificAdd(B, 4f, 20f);
                    }
                });

        assertEquals(List.of(A, B), order);
        assertEquals(5f, report.attempts().get(1).sourceSnapshot().amount());
        assertEquals(5f, report.attempts().get(1).actualRemoved());
    }

    @Test
    void sharedLivingProfileHasExactStageOrderAndCap() {
        assertEquals(List.of(MetabolismStage.RESPIRATION, MetabolismStage.DIGESTION,
                MetabolismStage.BLOODSTREAM, MetabolismStage.METABOLITES),
                MetabolizerProfile.SHARED_LIVING.stages());
        assertEquals(2, MetabolizerProfile.SHARED_LIVING.perStageProcessCap());
    }

    @Test
    void reportAttemptsAndNestedAccountingAreImmutable() {
        MetabolismReport report = run(new ReagentAttachment(Map.of(A, 1f)),
                Map.of(A.location(), reagent("a", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(B, 1f), 1f))), 10f,
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1));
        MetabolismReport.MetabolismAttempt attempt = report.attempts().get(0);

        assertThrows(UnsupportedOperationException.class, () -> report.attempts().clear());
        assertThrows(UnsupportedOperationException.class, () -> attempt.requestedProduction().put(B, 2f));
        assertThrows(UnsupportedOperationException.class, () -> attempt.sourceSnapshot().quantities().put(A, 2f));
        assertThrows(UnsupportedOperationException.class, () -> attempt.retained().put(B, 2f));
    }

    @Test
    void missingPrototypeStageAndZeroEntriesDoNotConsumeTheStageCap() {
        ResourceKey<ReagentData> missing = ModReagents.createKey("kernel-missing");
        ResourceKey<ReagentData> wrongStage = ModReagents.createKey("kernel-wrong-stage");
        ResourceKey<ReagentData> zero = ModReagents.createKey("kernel-zero");
        ResourceKey<ReagentData> valid = ModReagents.createKey("kernel-valid");
        MetabolizerProfile profile = new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1);
        Map<ResourceLocation, ReagentData> catalog = Map.of(
                wrongStage.location(), reagent("wrong", MetabolismStage.DIGESTION,
                        new MetabolismData(List.of(), Map.of(), 1f)),
                zero.location(), reagent("zero", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 1f)),
                valid.location(), reagent("valid", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 1f)));

        // The tiny random source makes the candidate order explicit. The
        // cap-one runs then prove that a skip did not consume the only
        // available work slot.
        MetabolizerProfile controlProfile = new MetabolizerProfile(
                List.of(MetabolismStage.RESPIRATION), 2);
        Map<ResourceLocation, ReagentData> wrongStageControl = Map.of(
                wrongStage.location(), reagent("wrong", MetabolismStage.RESPIRATION,
                        new MetabolismData(List.of(), Map.of(), 1f)),
                valid.location(), catalog.get(valid.location()));
        assertEquals(wrongStage, order(new ReagentAttachment(Map.of(wrongStage, 1f, valid, 1f)),
                wrongStageControl, 10f, controlProfile, new FixedRandomSource(0)).get(0));
        assertEquals(List.of(valid), order(new ReagentAttachment(Map.of(wrongStage, 1f, valid, 1f)),
                catalog, 10f, profile, new FixedRandomSource(0)));

        Map<ResourceLocation, ReagentData> missingControl = Map.of(
                missing.location(), catalog.get(valid.location()),
                valid.location(), catalog.get(valid.location()));
        assertEquals(missing, order(new ReagentAttachment(Map.of(missing, 1f, valid, 1f)),
                missingControl, 10f, controlProfile, new FixedRandomSource(1)).get(0));
        assertEquals(List.of(valid), order(new ReagentAttachment(Map.of(missing, 1f, valid, 1f)),
                catalog, 10f, profile, new FixedRandomSource(1)));

        assertEquals(zero, order(new ReagentAttachment(Map.of(zero, 1f, valid, 1f)),
                catalog, 10f, controlProfile, new FixedRandomSource(0)).get(0));
        assertEquals(List.of(valid), order(new ReagentAttachment(Map.of(zero, 0f, valid, 1f)),
                catalog, 10f, profile, new FixedRandomSource(0)));
    }

    @Test
    void callbackSeesPostRemovalSnapshotAndProductsAreAddedWhenItThrows() {
        ReagentData source = reagent("a", MetabolismStage.RESPIRATION,
                new MetabolismData(List.of(), Map.of(B, 1f), 1f));
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 2f));
        List<Float> observedLiveAmounts = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> MetabolismSystem.process(attachment,
                Map.of(A.location(), source), new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1),
                10f, RandomSource.create(4L), invocation -> {
                    observedLiveAmounts.add(attachment.getMap().getOrDefault(A, 0f));
                    assertEquals(2f, invocation.sourceSnapshot().amount());
                    assertEquals(2f, invocation.sourceSnapshot().quantities().get(A));
                    assertThrows(UnsupportedOperationException.class,
                            () -> invocation.sourceSnapshot().quantities().put(A, 9f));
                    assertTrue(!attachment.getMap().containsKey(B));
                    throw new IllegalStateException("effect failure");
                }));
        assertEquals(List.of(1f), observedLiveAmounts);
        assertEquals(1f, attachment.getMap().get(B));
    }

    @Test
    void callbackFailureRemainsPrimaryWhenProductAdditionStillRuns() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f));
        IllegalStateException callbackFailure = new IllegalStateException("effect failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> MetabolismSystem.process(attachment,
                        Map.of(A.location(), reagent("a", MetabolismStage.RESPIRATION,
                                new MetabolismData(List.of(), Map.of(B, 1f), 1f))),
                        new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1), 10f,
                        RandomSource.create(4L), invocation -> {
                            throw callbackFailure;
                        }));

        assertSame(callbackFailure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(1f, attachment.getMap().get(B));
    }

    @Test
    void errorCallbackTypeIsPreservedWhileProductsAreStillAdded() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f));
        AssertionError callbackFailure = new AssertionError("effect failure");

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> MetabolismSystem.process(attachment,
                        Map.of(A.location(), reagent("a", MetabolismStage.RESPIRATION,
                                new MetabolismData(List.of(), Map.of(B, 1f), 1f))),
                        new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1), 10f,
                        RandomSource.create(4L), invocation -> { throw callbackFailure; }));

        assertSame(callbackFailure, thrown);
        assertEquals(1f, attachment.getMap().get(B));
    }

    @Test
    void invalidCapacityIsRejectedBeforeRemoval() {
        ReagentAttachment attachment = new ReagentAttachment(Map.of(A, 1f));
        assertThrows(IllegalArgumentException.class, () -> MetabolismSystem.process(attachment,
                Map.of(), MetabolizerProfile.SHARED_LIVING, .5f, RandomSource.create(), invocation -> { }));
        assertEquals(1f, attachment.getMap().get(A));
        assertThrows(IllegalArgumentException.class,
                () -> new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 0));
    }

    @Test
    void centNativeMetabolismFloorsProductsAndNeverRemovesMoreThanSource() {
        var plan = MetabolismMath.metabolizeUnits(12L, .125f, Map.of(A, .125f, B, .005f, C, .01f));
        assertEquals(12L, plan.actualRemovedUnits());
        assertEquals(1f, plan.scale());
        assertEquals(Map.of(A, 1L, B, 0L, C, 0L), plan.requestedProductionUnits());
        assertEquals(6L, MetabolismMath.metabolizeUnits(12L, .125f, Map.of(A, .5f))
                .requestedProductionUnits().get(A));

        ReagentAttachment source = new ReagentAttachment(Map.of(A, .01f));
        var tooFast = MetabolismMath.metabolizeUnits(source.snapshotUnits().get(A), 1f, Map.of(B, 1f));
        assertEquals(source.snapshotUnits().get(A), tooFast.actualRemovedUnits());
    }

    @Test
    void configuredPositiveRateBelowOneCentFailsInsteadOfStalling() {
        assertThrows(IllegalArgumentException.class,
                () -> MetabolismMath.metabolizeUnits(100L, .005f, Map.of(B, 1f)));
    }

    @Test
    void highValidMetadataRateDoesNotUseFloatSnapshotForSourceAuthority() {
        ReagentAttachment source = new ReagentAttachment(Map.of(A, 200_000.01f));
        ReagentData reagent = reagent("high-rate", MetabolismStage.RESPIRATION,
                new MetabolismData(List.of(), Map.of(B, .5f), 200_000.01f));
        var report = MetabolismSystem.process(source, Map.of(A.location(), reagent),
                new MetabolizerProfile(List.of(MetabolismStage.RESPIRATION), 1), 200_000.01f,
                RandomSource.create(1), invocation -> { });
        assertTrue(!source.snapshotUnits().containsKey(A));
        // At this magnitude the float literal 200_000.01f is represented by
        // Float.toString as 200_000.02, so decimal-boundary conversion uses
        // that actual canonical float value rather than biasing it down.
        assertEquals(100_000.01f, report.attempts().get(0).capacity().retained().get(B));
    }

    private static MetabolismReport run(ReagentAttachment attachment,
                                        Map<ResourceLocation, ReagentData> catalog,
                                        float capacity,
                                        MetabolizerProfile profile) {
        return MetabolismSystem.process(attachment, catalog, profile, capacity,
                RandomSource.create(123L), invocation -> { });
    }

    private static List<ResourceKey<ReagentData>> order(ReagentAttachment attachment,
                                                         Map<ResourceLocation, ReagentData> catalog,
                                                         float capacity,
                                                         MetabolizerProfile profile,
                                                         RandomSource random) {
        return MetabolismSystem.process(attachment, catalog, profile, capacity, random, invocation -> { })
                .attempts().stream().map(MetabolismReport.MetabolismAttempt::reagent).toList();
    }

    private static Map<ResourceKey<ReagentData>, Float> reverse(Map<ResourceKey<ReagentData>, Float> source) {
        List<Map.Entry<ResourceKey<ReagentData>, Float>> entries = new ArrayList<>(source.entrySet());
        Map<ResourceKey<ReagentData>, Float> reversed = new LinkedHashMap<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<ResourceKey<ReagentData>, Float> entry = entries.get(i);
            reversed.put(entry.getKey(), entry.getValue());
        }
        return reversed;
    }

    private static List<ResourceKey<ReagentData>> canonicalSortKeys() {
        return List.of(
                ModReagents.createKey("kernel-sort-d"),
                ModReagents.createKey("kernel-sort-b"),
                ModReagents.createKey("kernel-sort-a"),
                ModReagents.createKey("kernel-sort-c"));
    }

    private static final class FixedRandomSource implements RandomSource {
        private final int nextInt;

        private FixedRandomSource(int nextInt) {
            this.nextInt = nextInt;
        }

        @Override
        public RandomSource fork() {
            return this;
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return null;
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public int nextInt() {
            return nextInt;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(nextInt, bound);
        }

        @Override
        public long nextLong() {
            return nextInt;
        }

        @Override
        public boolean nextBoolean() {
            return nextInt != 0;
        }

        @Override
        public float nextFloat() {
            return nextInt == 0 ? 0f : 1f;
        }

        @Override
        public double nextDouble() {
            return nextInt == 0 ? 0d : 1d;
        }

        @Override
        public double nextGaussian() {
            return nextInt;
        }
    }

    private static ReagentData reagent(String id, Object... metabolism) {
        Map<MetabolismStage, MetabolismData> values = new HashMap<>();
        for (int i = 0; i < metabolism.length; i += 2) {
            values.put((MetabolismStage) metabolism[i], (MetabolismData) metabolism[i + 1]);
        }
        return new ReagentData(id, id, "test", "", "", "", 0, Map.of(), 400f, 200f,
                ContrabandSeverityEnum.NONE, List.of(), values, new MetamorphicSpriteData(null, null),
                0f, "fill-", false, 0f);
    }

    private static ReagentData reagent(String id) {
        return reagent(id, new Object[0]);
    }
}
