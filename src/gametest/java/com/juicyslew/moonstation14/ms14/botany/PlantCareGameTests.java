package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-genetics", "botany-care"})
public final class PlantCareGameTests {
    private PlantCareGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void ammoniaAppliesResolvedNutritionAndHealthAtOrdinaryPoll(GameTestHelper helper) {
        float[] baseline = new float[4];
        long[] initialCycle = new long[1];
        prepare(helper, "ammonia", (tray, plant) -> {
            plant.state(plant.age(), 5f, plant.lastProduceAge(), plant.skipAging(), plant.ready(), plant.dead());
            plant.soil(plant.water(), 50f, plant.weeds(), plant.soilToxins());
            baseline[0] = plant.health(); baseline[1] = plant.nutrition();
            baseline[2] = plant.effectiveProfile().biology().endurance(); initialCycle[0] = plant.lastCycle();
        }, (tray, plant, remaining) -> {
            require(remaining == 0, "one ordinary poll drains exactly one unit");
            require(plant.lastCycle() == initialCycle[0], "ammonia result precedes the 300-tick growth cycle");
            require(Math.abs(plant.health() - 5.5f) < .001 && plant.health() <= baseline[2],
                    "ammonia applies fixed +0.5 health below endurance; baseline=" + baseline[0] + " endurance=" + baseline[2]
                            + " actual=" + plant.health() + " cycle=" + plant.lastCycle());
            require(Math.abs(plant.nutrition() - (baseline[1] + 1f)) < .001,
                    "ammonia applies fixed +1 nutrition; baseline=" + baseline[1] + " actual=" + plant.nutrition());
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void pestKillerRaisesToxinsAndClampsPestsAfterOrdinaryPoll(GameTestHelper helper) {
        prepare(helper, "pestkiller", (tray, plant) -> { plant.soil(plant.water(), plant.nutrition(), plant.weeds(), 2); plant.pestLevel(2); },
                (tray, plant, remaining) -> {
                    require(remaining == 0, "pest killer dose drained");
                    require(plant.soilToxins() == 6f, "pest killer adds four toxins");
                    require(plant.pests() == 0f, "minus six pest adjustment clamps arranged two pests to zero");
                });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void plantBGoneKillsBeforeLaterMutationModifierEffect(GameTestHelper helper) {
        prepare(helper, "plantbgone", (tray, plant) -> {
            plant.soil(plant.water(), plant.nutrition(), 5, 0);
            plant.mutationModifier(2f);
            plant.state(plant.age(), 10f, plant.lastProduceAge(), plant.skipAging(), false, false);
        }, (tray, plant, remaining) -> {
            require(remaining == 0, "plant-b-gone dose drained");
            require(plant.dead() && !plant.ready() && plant.health() == 0,
                    "minus twenty health kills low-health plant immediately");
            require(plant.soilToxins() == 6f, "plant-b-gone adds six toxins before death");
            require(plant.weeds() == 0f, "weed reduction then death bonus is clamped at final poll sanity");
            require(plant.mutationModifier() == 1f, "later mutation modifier is not applied after death resets mutation state");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void robustHarvestChangesResolvedGeneticsWithoutHarvestOutput(GameTestHelper helper) {
        prepare(helper, "robustharvest", (tray, plant) -> {
            var before = plant.genetics();
            require(!before.profile().sourceName().isBlank(), "resolved original source name is present");
            var life = before.profile().lifecycle();
            var markedChemical = ResourceLocation.fromNamespaceAndPath("moonstation14", "care_test_marker");
            var chemicals = new java.util.HashMap<>(before.profile().chemicals());
            chemicals.put(markedChemical, new SeedProfile.Chemical(0, 1, 1, false));
            var markedProfile = new SeedProfile(before.profile().packet(), before.profile().product(), before.profile().visualKey(),
                    before.profile().biology(), new SeedProfile.Lifecycle(life.growthStages(), life.lifespan(), life.maturation(),
                    life.production(), 2, 29, life.harvest(), true, false), chemicals,
                    java.util.Map.of(GasType.OXYGEN, 0.25f), java.util.Map.of(GasType.CARBON_DIOXIDE, 0.2f),
                    before.profile().mutationTargets(), before.profile().scream(), before.profile().sourceName());
            plant.genetics(new GeneticValue(before.species(), markedProfile));
        }, (tray, plant, remaining) -> {
            require(remaining == 0, "robust harvest dose drained");
            var after = plant.genetics();
            var life = after.profile().lifecycle();
            require(after.profile().sourceName().equals(ModSeedProfiles.serverLookup(after.species()).sourceName()),
                    "ordinary care mutation retains original resolved source name");
            require(life.potency() == 32f && life.seedless(), "robust harvest raises 29 potency to 32 and seedless above threshold 30");
            require(life.yield() == 2, "below-limit robust harvest does not consume yield");
            require(after.profile().chemicals().containsKey(ResourceLocation.fromNamespaceAndPath("moonstation14", "care_test_marker")),
                    "care mutation retains unrelated marked chemical profile");
            require(after.profile().consumeGasses().get(GasType.OXYGEN) == .25f
                    && after.profile().exudeGasses().get(GasType.CARBON_DIOXIDE) == .2f,
                    "care mutation retains gas profile");
            require(after.profile().mutationTargets().equals(ModSeedProfiles.serverLookup(after.species()).mutationTargets()),
                    "care mutation retains resolved mutation targets");
            require(tray.autoHarvestOutcome() == HydroponicsTrayBlockEntity.AutoHarvestOutcome.NONE,
                    "robust metabolism does not produce harvest output");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void phalanximineBelow399CentsDoesNotRestoreViability(GameTestHelper helper) {
        float[] toxin = new float[1];
        prepareAmount(helper, "phalanximine", 399, (tray, plant) -> {
            toxin[0] = plant.soilToxins();
            var old = plant.genetics();
            plant.genetics(withViability(old, false));
        }, (tray, plant, remaining) -> {
            require(remaining == 299, "399-cent dose loses exactly one ordinary 100-cent poll");
            require(plant.genetics().profile().lifecycle().viable() == false,
                    "399-cent phalanximine dose remains below resolved minscale 4");
            require(plant.soilToxins() == toxin[0] + 6, "subthreshold phalanximine still applies fixed six toxins");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void phalanximineAt400CentsRestoresViability(GameTestHelper helper) {
        float[] toxin = new float[1];
        prepareAmount(helper, "phalanximine", 400, (tray, plant) -> {
            toxin[0] = plant.soilToxins();
            plant.genetics(withViability(plant.genetics(), false));
        }, (tray, plant, remaining) -> {
            require(remaining == 300, "400-cent dose loses exactly one ordinary 100-cent poll");
            require(plant.genetics().profile().lifecycle().viable(), "400-cent phalanximine dose reaches minscale 4 and restores viability");
            require(plant.soilToxins() == toxin[0] + 6, "threshold phalanximine applies fixed six toxins, not dose-scaled toxins");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void cryoxadoneForcesImmaturePlantRefreshAtOrdinaryPoll(GameTestHelper helper) {
        float[] start = new float[5];
        prepare(helper, "cryoxadone", (tray, plant) -> {
            var lifecycle = plant.genetics().profile().lifecycle();
            float age = lifecycle.maturation() / 2f;
            require(plant.effectiveProfile().biology().toxinTolerance() == 4f,
                    "resolved wheat toxin tolerance matches native fixture assumption: "
                            + plant.effectiveProfile().biology().toxinTolerance());
            plant.state(age, 5f, 0, 10f, false, false);
            plant.soil(plant.water(), plant.nutrition(), 0f, 9f);
            plant.pestLevel(0f);
            start[0] = age;
            start[1] = plant.health();
            start[2] = plant.soilToxins();
            start[3] = (int) (lifecycle.maturation() / lifecycle.growthStages());
            start[4] = plant.lastCycle();
        }, (tray, plant, remaining) -> {
            require(remaining == 0, "cryoxadone dose drained by ordinary poll");
            float expectedAge = start[0] - start[3];
            require(plant.age() == expectedAge, "immature cryoxadone uses truncating maturation/growth-stage deviation: expected="
                    + expectedAge + " actual=" + plant.age());
            require(plant.lastProduceAge() == expectedAge, "cryoxadone refresh synchronizes last-produced age to exact immature result");
            require(plant.skipAging() == 10f, "cryo adds one skip tick and forced ordinary cycle consumes one");
            require(plant.lastCycle() > (long) start[4], "cryoxadone force-update runs the ordinary cycle before its 300-tick deadline");
            require(plant.soilToxins() == 3f, "cryoxadone takes toxins 9 to 4, then forced ordinary cycle removes one; actual="
                    + plant.soilToxins());
            require(plant.health() == 10f && plant.health() == start[1] + 5f,
                    "cryoxadone adds exactly five health; skip-aging suppresses random ordinary health gains and toxins equal tolerance; actual="
                            + plant.health() + " initial=" + start[1] + " toxins=" + plant.soilToxins());
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    @GameTestTags("botany-seed-reference")
    public static void ammoniaCarePollDetachesNonuniqueSeedReference(GameTestHelper helper) {
        PlantSeedReference[] captured = new PlantSeedReference[1];
        prepareAmount(helper, "ammonia", 1, (tray, plant) -> captured[0] = plant.seedReference(),
                (tray, plant, remaining) -> {
                    var now = plant.seedReference();
                    require(remaining == 0, "one-cent ammonia dose drains on ordinary poll");
                    require(now != captured[0] && now.unique(), "ordinary ammonia care poll detaches plant seed reference");
                    require(!captured[0].unique() && captured[0].current().equals(now.current()),
                            "detachment leaves captured nonunique reference and genetics snapshot unchanged");
                });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    @GameTestTags("botany-seed-reference")
    public static void mutationBlockedAmmoniaPollPreservesNonuniqueSeedReference(GameTestHelper helper) {
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            try {
                ServerLevel level = (ServerLevel) helper.getLevel();
                var seedId = ModSeedProfiles.seedId("wheat");
                var seed = ModSeedProfiles.serverLookup(seedId);
                var ammonia = ModReagents.createKey("ammonia");
                require(seed != null && PrototypeRuntime.serverReagents().get(ammonia.location()) != null,
                        "resolved wheat and server ammonia prototypes exist");
                tray.commitPlant(seedId, seed, level.getGameTime());
                PlantInstance plant = tray.plantState();
                plant.adjustMutationLevel(25f);
                PlantSeedReference captured = plant.seedReference();
                long initialNextUpdate = plant.nextUpdate();
                var oldSoil = tray.soilSolution();
                var soil = new ReagentAttachment(oldSoil.toComponent());
                require(soil.admitUnits(ammonia, 1, tray.resolvedTrayProfile().soilCapacityCents()) == 1,
                        "arrange one cent of ammonia in actual soil");
                require(tray.tryReplaceSoil(tray.placementIdentity(), plant, plant.revision(), oldSoil, soil),
                        "tray accepts dose for admission-blocked poll");
                awaitBlockedReferencePoll(helper, tray, ammonia, plant, captured, initialNextUpdate, 0);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static void awaitBlockedReferencePoll(GameTestHelper helper, HydroponicsTrayBlockEntity tray,
            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,
            PlantInstance plant, PlantSeedReference captured, long initialNextUpdate, int waited) {
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            try {
                if (plant.nextUpdate() > initialNextUpdate) {
                    require(tray.soilSolution().snapshotUnits().getOrDefault(reagent, 0L) == 1,
                            "mutation-blocked poll leaves one-cent ammonia dose undrained");
                    require(plant.seedReference() == captured && !captured.unique(),
                            "mutation-blocked ordinary poll preserves original nonunique seed reference");
                    helper.succeed();
                    return;
                }
                if (waited >= 60) throw new AssertionError("ordinary tray poll did not reach mutation admission boundary");
                awaitBlockedReferencePoll(helper, tray, reagent, plant, captured, initialNextUpdate, waited + 1);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 100)
    public static void mutationLevelAtAdmissionLimitBlocksCareEffectsButNotSoilPoll(GameTestHelper helper) {
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            try {
                ServerLevel level = (ServerLevel) helper.getLevel();
                var seedId = ModSeedProfiles.seedId("wheat");
                var seed = ModSeedProfiles.serverLookup(seedId);
                var ammonia = ModReagents.createKey("ammonia");
                require(seed != null && PrototypeRuntime.serverReagents().get(ammonia.location()) != null,
                        "resolved wheat and server ammonia prototypes exist");
                tray.commitPlant(seedId, seed, level.getGameTime());
                PlantInstance plant = tray.plantState();
                plant.state(plant.age(), 5f, plant.lastProduceAge(), plant.skipAging(), plant.ready(), plant.dead());
                plant.adjustMutationLevel(25f);
                long initialCycle = plant.lastCycle();
                long initialNextUpdate = plant.nextUpdate();
                float nutrition = plant.nutrition();
                var oldSoil = tray.soilSolution();
                var soil = new ReagentAttachment(oldSoil.toComponent());
                require(soil.admitUnits(ammonia, 100, tray.resolvedTrayProfile().soilCapacityCents()) == 100,
                        "arrange one unit of ammonia in actual soil");
                require(tray.tryReplaceSoil(tray.placementIdentity(), plant, plant.revision(), oldSoil, soil),
                        "tray accepts dose for admission-boundary poll");
                awaitBlockedCarePoll(helper, tray, ammonia, plant, initialCycle, initialNextUpdate, nutrition, 0);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static void awaitBlockedCarePoll(GameTestHelper helper, HydroponicsTrayBlockEntity tray,
            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,
            PlantInstance plant, long initialCycle, long initialNextUpdate, float nutrition, int waited) {
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            try {
                if (plant.nextUpdate() > initialNextUpdate) {
                    require(plant.lastCycle() == initialCycle, "admission boundary observed before ordinary growth cycle");
                    require(plant.mutationLevel() == 25f, "blocked care poll leaves mutation level at admission limit");
                    require(tray.soilSolution().snapshotUnits().getOrDefault(reagent, 0L) == 100,
                            "admission-blocked reagent is not drained");
                    require(plant.health() == 5f && plant.nutrition() == nutrition,
                            "admission-blocked poll applies neither ammonia health nor nutrition");
                    helper.succeed();
                    return;
                }
                if (waited >= 60) throw new AssertionError("ordinary tray poll did not advance nextUpdate; nextUpdate="
                        + plant.nextUpdate() + " expectedAfter=" + initialNextUpdate + " cycle=" + plant.lastCycle());
                awaitBlockedCarePoll(helper, tray, reagent, plant, initialCycle, initialNextUpdate, nutrition, waited + 1);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static GeneticValue withViability(GeneticValue value, boolean viable) {
        var p = value.profile(); var old = p.lifecycle();
        var life = new SeedProfile.Lifecycle(old.growthStages(), old.lifespan(), old.maturation(), old.production(),
                old.yield(), old.potency(), old.harvest(), viable, old.seedless());
        return new GeneticValue(value.species(), new SeedProfile(p.packet(), p.product(), p.visualKey(), p.biology(), life,
                p.chemicals(), p.consumeGasses(), p.exudeGasses(), p.mutationTargets(), p.scream(), p.sourceName()));
    }

    private static void prepareAmount(GameTestHelper helper, String reagentId, long amount, Arrange arrange, Verify verify) {
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            try {
                ServerLevel level = (ServerLevel) helper.getLevel();
                var seedId = ModSeedProfiles.seedId("wheat");
                var seed = ModSeedProfiles.serverLookup(seedId);
                var reagent = ModReagents.createKey(reagentId);
                require(seed != null && PrototypeRuntime.serverReagents().get(reagent.location()) != null,
                        "resolved wheat and server " + reagentId + " prototypes exist");
                tray.commitPlant(seedId, seed, level.getGameTime());
                PlantInstance plant = tray.plantState();
                arrange.apply(tray, plant);
                var oldSoil = tray.soilSolution();
                var soil = new ReagentAttachment(oldSoil.toComponent());
                require(soil.admitUnits(reagent, amount, tray.resolvedTrayProfile().soilCapacityCents()) == amount,
                        "arrange exact " + amount + "-cent dose in actual soil");
                require(tray.tryReplaceSoil(tray.placementIdentity(), plant, plant.revision(), oldSoil, soil),
                        "tray accepts resolved reagent dose");
                awaitPoll(helper, tray, reagent, plant, verify, Math.max(0, amount - 100), 0);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    @FunctionalInterface private interface Arrange { void apply(HydroponicsTrayBlockEntity tray, PlantInstance plant); }
    @FunctionalInterface private interface Verify { void apply(HydroponicsTrayBlockEntity tray, PlantInstance plant, long remaining); }
    private static void prepare(GameTestHelper helper, String reagentId, Arrange arrange, Verify verify) {
        prepareAmount(helper, reagentId, 100, arrange, verify);
    }

    private static void awaitPoll(GameTestHelper helper, HydroponicsTrayBlockEntity tray,
            net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> reagent,
            PlantInstance plant, Verify verify, long expectedRemaining, int waited) {
        BoundedGameTestWait.safeDelay(helper, 1, () -> {
            try {
                long remaining = tray.soilSolution().snapshotUnits().getOrDefault(reagent, 0L);
                if (remaining == expectedRemaining) {
                    verify.apply(tray, plant, remaining);
                    helper.succeed();
                    return;
                }
                if (remaining < expectedRemaining) throw new AssertionError("ordinary poll passed expected first-poll reagent remainder; reagent="
                        + reagent.location() + " expected=" + expectedRemaining + " actual=" + remaining + " waited=" + waited);
                if (waited >= 60) throw new AssertionError("reagent was not consumed by ordinary tray poll; reagent="
                        + reagent.location() + " remaining=" + remaining);
                awaitPoll(helper, tray, reagent, plant, verify, expectedRemaining, waited + 1);
            } catch (Throwable failure) { helper.testInfo.fail(failure); }
        });
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
