package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.MovementSpeedProjection;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.blood.BloodReducer;
import com.juicyslew.moonstation14.ms14.blood.BloodSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.ms14.thirst.ThirstComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerAttachment;
import com.juicyslew.moonstation14.ms14.hunger.HungerComponent;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.util.RandomSource;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StomachGameTests {
    private static final ResourceKey<ReagentData> WATER = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private static final ResourceKey<ReagentData> SUGAR = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "sugar"));
    private static final ResourceKey<ReagentData> MILK = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "milk"));
    private static final ResourceKey<ReagentData> SULFURIC_ACID = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "sulfuricacid"));
    private static final ResourceKey<ReagentData> INVALID = ResourceKey.create(
            com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,
            ResourceLocation.fromNamespaceAndPath("moonstation14", "nonexistent_test"));

    private StomachGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bottleDoseEntersStomachAndFullStomachPreservesSource(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 6f)));
        float moved = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), 3f);
        require(moved == 3f, "bottle dose should be admitted to stomach");
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().get(WATER) == 3f,
                "stomach owns the consumed bottle dose");
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER) == 3f,
                "bottle source is reduced by the admitted quantity");

        MS14Provider.update(character, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, StomachSystem.CAPACITY)));
        float sourceBefore = bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER);
        require(StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), 3f) == 0f, "full stomach rejects dose");
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER) == sourceBefore,
                "full stomach does not consume source");
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().equals(
                        Map.of(WATER, StomachSystem.CAPACITY)),
                "full stomach remains unchanged");
        require(StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), Float.NaN) == 0f, "invalid dose is rejected");
        require(bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER) == sourceBefore,
                "invalid dose does not consume source");
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().equals(
                        Map.of(WATER, StomachSystem.CAPACITY)),
                "invalid dose leaves stomach unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedSourceNearCapacityConservesEachReagent(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 4f, SUGAR, 4f)));
        MS14Provider.update(character, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, 48f, SUGAR, 1f)));

        float accepted = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), 4f);
        Map<ResourceKey<ReagentData>, Float> sourceAfter = bottle.get(ModDataComponents.REAGENT.get()).contents();
        Map<ResourceKey<ReagentData>, Float> stomachAfter = MS14Provider.get(character, MS14Bridges.STOMACH).getMap();
        long waterSourceDelta = ReagentUnits.fromFloat(4f) - ReagentUnits.fromFloat(sourceAfter.get(WATER));
        long sugarSourceDelta = ReagentUnits.fromFloat(4f) - ReagentUnits.fromFloat(sourceAfter.get(SUGAR));
        long waterStomachDelta = ReagentUnits.fromFloat(stomachAfter.get(WATER)) - ReagentUnits.fromFloat(48f);
        long sugarStomachDelta = ReagentUnits.fromFloat(stomachAfter.get(SUGAR)) - ReagentUnits.fromFloat(1f);

        require(accepted == 1f, "near-capacity admission reports actual accepted dose");
        require(waterSourceDelta == 50L && waterStomachDelta == 50L,
                "water source and stomach deltas match exactly");
        require(sugarSourceDelta == 50L && sugarStomachDelta == 50L,
                "sugar source and stomach deltas match exactly");
        require(waterSourceDelta + sugarSourceDelta == ReagentUnits.fromFloat(accepted),
                "reported dose equals total source delta");
        require(waterStomachDelta + sugarStomachDelta == ReagentUnits.fromFloat(accepted),
                "reported dose equals total destination delta");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void halfFullStomachAcceptsPartialSipFromDecimalSource(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, 10.1f)));
        MS14Provider.update(character, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, 25f)));

        float accepted = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), 5f);
        long sourceBefore = ReagentUnits.fromFloat(10.1f);
        long sourceAfter = ReagentUnits.fromFloat(bottle.get(ModDataComponents.REAGENT.get()).contents().get(WATER));
        long stomachBefore = ReagentUnits.fromFloat(25f);
        long stomachAfter = ReagentUnits.total(ReagentUnits.fromMap(
                MS14Provider.get(character, MS14Bridges.STOMACH).getMap()).values());
        require(accepted == 5f, "half-full stomach accepts available partial sip");
        require(sourceBefore - sourceAfter == 500L && stomachAfter - stomachBefore == 500L,
                "source and stomach snapshots conserve exactly 5.00 units");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void subcentSourceIsNoOpAndLegacyStomachNormalizesOnPositiveSip(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        ItemStack bottle = new ItemStack(ModItems.BOTTLE.get());
        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, .005f)));
        float refused = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), .1f);
        require(refused == 0f && bottle.get(ModDataComponents.REAGENT.get()).contents().getOrDefault(WATER, 0f) == 0f,
                "sub-cent-only source does not persist or repeatedly consume a phantom cent");

        bottle.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, .01f)));
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(WATER, 49.999f)));
        float accepted = StomachSystem.ingest(ModItems.BOTTLE.get().toHandle(bottle), character,
                (ServerLevel) helper.getLevel(), .1f);
        Map<ResourceKey<ReagentData>, Float> sourceAfter = bottle.get(ModDataComponents.REAGENT.get()).contents();
        Map<ResourceKey<ReagentData>, Float> stomachAfter = MS14Provider.get(character, MS14Bridges.STOMACH).getMap();
        require(accepted == .01f, "legacy 49.999 rounds down to 4,999 cents and admits one cent");
        require(ReagentUnits.fromMap(sourceAfter).getOrDefault(WATER, 0L) == 0L
                        && ReagentUnits.total(ReagentUnits.fromMap(stomachAfter).values()) == 5_000L,
                "the positive mutation normalizes legacy residue once and conserves the admitted cent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void puddleDoseRequiresEligibilityAndGenericTransferIsUnchanged(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        BlockPos puddlePos = new BlockPos(4, 1, 1);
        helper.getLevel().setBlock(puddlePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        PuddleBlockEntity puddle = (PuddleBlockEntity) helper.getLevel().getBlockEntity(puddlePos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 4f)));
        require(StomachSystem.ingest(puddle.toHandleSelf(), character, (ServerLevel) helper.getLevel(), 2f) == 2f,
                "puddle drinking dose should enter stomach");
        require(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().get(WATER) == 2f,
                "puddle source loses only admitted dose");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 1, 1));
        require(StomachSystem.ingest(puddle.toHandleSelf(), zombie, (ServerLevel) helper.getLevel(), 1f) == 0f,
                "noneligible zombie has no ingestion stomach");
        require(!zombie.hasData(ModDataAttachments.STOMACH.get()), "unsupported ingestion creates no empty stomach attachment");

        // A living entity without a mapped blood policy is not a generic solution container.
        ReagentSystem.handleTransfer(puddle.toHandleSelf(), ((IReagentTrait) zombie).toHandleSelf(),
                helper.getLevel(), 1f);
        require(!zombie.hasData(ModDataAttachments.REAGENT.get())
                        && !zombie.hasData(ModDataAttachments.BLOODSTREAM.get())
                        && MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap().get(WATER) == 2f,
                "unconfigured living targets reject generic transfer without consuming the puddle source");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stomachDigestionPersistsAndAppliesScaledThirstOnce(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(MILK, 1f)));
        MS14Provider.update(character, MS14Bridges.THIRST, new ThirstAttachment(new ThirstComponent(300f)));
        EntityActivitySystem.update(character, EntityActivity.REAGENT_METABOLISM, true);
        require(character.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()).isActive(EntityActivity.REAGENT_METABOLISM),
                "stomach contents enroll metabolism activity");

        TickHooks.runDueActivities(character, (ServerLevel) helper.getLevel(), java.util.Set.of(EntityActivity.REAGENT_METABOLISM));
        Map<ResourceKey<ReagentData>, Float> stomach = MS14Provider.get(character, MS14Bridges.STOMACH).getMap();
        require(stomach.getOrDefault(MILK, 0f) < 1f, "milk is consumed from stomach");
        float removed = 1f - stomach.getOrDefault(MILK, 0f);
        require(Math.abs(MS14Provider.get(character, MS14Bridges.THIRST).thirst()
                        - (300f + 4f * (removed / .5f))) < .0001f,
                "milk thirst effect uses metabolism's actual removal scale");
        require(!character.hasData(ModDataAttachments.REAGENT.get())
                        && !MS14Provider.get(character, MS14Bridges.BLOODSTREAM).getMap().containsKey(MILK),
                "milk digestion does not double-process through shared body metabolism");
        require(Math.abs(stomach.getOrDefault(MILK, 0f) - .5f) < .0001f,
                "one due metabolism pass removes only the configured half-unit rate");
        require(character.hasData(ModDataAttachments.STOMACH.get()), "changed stomach attachment is persisted");
        require(character.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()).isActive(EntityActivity.REAGENT_METABOLISM),
                "remaining stomach reagent keeps metabolism activity enrolled");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nonDigestionStomachReagentTransfersAfterBodyPass(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(SULFURIC_ACID, 1f)));
        EntityActivitySystem.update(character, EntityActivity.REAGENT_METABOLISM, true);
        float healthBefore = character.getHealth();

        TickHooks.runDueActivities(character, (ServerLevel) helper.getLevel(), java.util.Set.of(EntityActivity.REAGENT_METABOLISM));
        require(Math.abs(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().get(SULFURIC_ACID) - .75f) < .000001f,
                "non-digestion stomach reagent removes at most the quarter-unit transfer rate");
        require(Math.abs(MS14Provider.get(character, MS14Bridges.BLOODSTREAM).getMap().get(SULFURIC_ACID) - .12f) < .000001f,
                "25 source cents floor to 12 body cents at half efficacy (13 cents intentionally lost)");
        require(character.getHealth() == healthBefore, "body metabolism does not run again in the stomach pass");

        TickHooks.runDueActivities(character, (ServerLevel) helper.getLevel(), java.util.Set.of(EntityActivity.REAGENT_METABOLISM));
        require(character.getHealth() < healthBefore, "12 body cents have actual metabolism scale 0.24 and run next pass");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullBodyRetainsNonDigestionStomachReagent(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment(Map.of(SULFURIC_ACID, 1f)));
        var policy = BloodSystem.resolvePolicy(character).orElseThrow(
                () -> new AssertionError("fixture host must resolve its prototype blood policy"));
        require(BloodSystem.reconcile(character), "fixture blood must initialize from its selected prototype");
        var body = MS14Provider.getDetached(character, MS14Bridges.BLOODSTREAM);
        long bodyCapacity = BloodReducer.capacity(policy);
        long bodyBefore = body.totalUnits();
        require(bodyBefore <= bodyCapacity, "prototype reference mixture must fit its configured capacity");
        var reference = BloodReducer.reference(policy);
        var fillReagent = reference.keySet().stream().sorted().findFirst().orElseThrow();
        require(body.admitUnits(fillReagent, bodyCapacity - bodyBefore, bodyCapacity)
                        == bodyCapacity - bodyBefore,
                "fixture must fill remaining capacity using a prototype reference reagent");
        MS14Provider.update(character, MS14Bridges.BLOODSTREAM, body);
        require(body.totalUnits() == bodyCapacity, "fixture must saturate its prototype-configured body capacity");
        var fullBodyBefore = MS14Provider.snapshot(body);
        EntityActivitySystem.update(character, EntityActivity.REAGENT_METABOLISM, true);

        TickHooks.runDueActivities(character, (ServerLevel) helper.getLevel(), java.util.Set.of(EntityActivity.REAGENT_METABOLISM));
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().get(SULFURIC_ACID) == 1f,
                "full body retains the stomach source reagent");
        require(MS14Provider.snapshot(MS14Provider.get(character, MS14Bridges.BLOODSTREAM))
                        .equals(fullBodyBefore), "full body remains unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vomitEmptiesStomachSpillsActualMixtureAndPenalizesCustomNeeds(GameTestHelper helper) {
        BlockPos supportPos = new BlockPos(1, 0, 1);
        helper.setBlock(supportPos, Blocks.STONE);
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setNoAi(true);
        MS14Provider.update(character, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, 1f, SUGAR, 1f)));
        MS14Provider.update(character, MS14Bridges.BLOODSTREAM, new ReagentAttachment(Map.of(MILK, 7f)));
        MS14Provider.update(character, MS14Bridges.HUNGER, new HungerAttachment(new HungerComponent(100f)));
        MS14Provider.update(character, MS14Bridges.THIRST, new ThirstAttachment(new ThirstComponent(100f)));

        EffectResult result = StomachSystem.vomit(vomitContext(character, (ServerLevel) helper.getLevel()));
        require(result == EffectResult.APPLIED, "eligible living stomach target should vomit");
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().isEmpty(), "vomit empties full stomach");
        require(MS14Provider.get(character, MS14Bridges.HUNGER).hunger() == 60f, "custom hunger loses 40 once");
        require(MS14Provider.get(character, MS14Bridges.THIRST).thirst() == 60f, "custom thirst loses 40 once");
        var vomitingSlowdown = ModStatusEffects.createKey("vomiting_slowdown");
        require(StatusEffectSystem.hasStatus(((IStatusEffectTrait) character).toHandleSelf(), helper.getLevel(), vomitingSlowdown),
                "vomit attaches canonical transient slowdown");
        require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(
                        MovementSpeedProjection.modifierId(vomitingSlowdown)).amount() == -.5d,
                "vomit projects 0.5 movement multiplier");
        require(MS14Provider.get(character, MS14Bridges.BLOODSTREAM).getMap().get(MILK) == 7f,
                "shared body solution is never purged");
        BlockPos expectedSupport = helper.absolutePos(supportPos);
        require(character.getOnPos().equals(expectedSupport), "vomiting target stands on its owned stone support");
        // vomit passes target.getOnPos() to handleSpillSolution, which creates the puddle
        // one block above that support. Inspect only this fixture-owned landing block: nearby
        // templates run in the same level and may independently create or consume puddles.
        BlockPos expectedLanding = expectedSupport.above();
        if (helper.getLevel().getBlockEntity(expectedLanding) instanceof PuddleBlockEntity puddle) {
            require(MS14Provider.get(puddle, MS14Bridges.REAGENT).getMap()
                            .equals(Map.of(WATER, 1f, SUGAR, 1f)),
                    "any successfully created vomit puddle preserves the actual mixture without synthetic reagent");
        }
        for (int tick = 0; tick < 10; tick++) StatusEffectSystem.advanceOneTick(character);
        require(StomachSystem.vomit(vomitContext(character, (ServerLevel) helper.getLevel()))
                        == EffectResult.APPLIED,
                "an already-empty eligible stomach is still handled");
        require(MS14Provider.get(character, MS14Bridges.HUNGER).hunger() == 20f
                        && MS14Provider.get(character, MS14Bridges.THIRST).thirst() == 20f,
                "empty-stomach handling applies the two need penalties exactly once more");
        require(StatusEffectSystem.hasStatus(((IStatusEffectTrait) character).toHandleSelf(), helper.getLevel(), vomitingSlowdown),
                "empty-stomach vomit refreshes rather than removing slowdown");
        require(MS14Provider.get(character, MS14Bridges.STATUS_EFFECT).get(vomitingSlowdown)
                        .orElseThrow().remainingDurationTicks().orElseThrow() == 267,
                "refresh restores duration to 267 ticks");
        for (int tick = 0; tick < 266; tick++) StatusEffectSystem.advanceOneTick(character);
        require(StatusEffectSystem.hasStatus(((IStatusEffectTrait) character).toHandleSelf(), helper.getLevel(), vomitingSlowdown),
                "267-tick status remains through tick 266");
        StatusEffectSystem.advanceOneTick(character);
        require(!StatusEffectSystem.hasStatus(((IStatusEffectTrait) character).toHandleSelf(), helper.getLevel(), vomitingSlowdown),
                "vomiting status expires on tick 267");
        require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(
                        MovementSpeedProjection.modifierId(vomitingSlowdown)) == null,
                "expiry removes only the vomiting movement projection");
        require(character.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(
                        com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "vomiting expiry preserves independent parched movement modifier");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vomitUnsupportedZombieDoesNotMutateSharedSolution(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        MS14Provider.update(zombie, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 2f)));
        EffectResult result = StomachSystem.vomit(vomitContext(zombie, (ServerLevel) helper.getLevel()));
        require(result == EffectResult.SKIPPED_UNSUPPORTED, "noneligible zombie is unsupported");
        require(MS14Provider.get(zombie, MS14Bridges.REAGENT).getMap().equals(Map.of(WATER, 2f)),
                "unsupported target leaves shared solution unchanged");
        require(!zombie.hasData(ModDataAttachments.STOMACH.get()), "unsupported target gets no stomach attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidStomachVomitFailsWithoutClearingOrApplyingPenalties(GameTestHelper helper) {
        Villager unknownOnly = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        unknownOnly.setNoAi(true);
        Map<ResourceKey<ReagentData>, Float> unknownContents = Map.of(INVALID, 2f);
        assertInvalidVomitIsNonMutating(helper, unknownOnly, unknownContents, 100f, 100f);

        Villager mixed = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 1));
        mixed.setNoAi(true);
        Map<ResourceKey<ReagentData>, Float> mixedContents = Map.of(WATER, 1f, INVALID, 2f);
        assertInvalidVomitIsNonMutating(helper, mixed, mixedContents, 120f, 130f);
        helper.succeed();
    }

    private static void assertInvalidVomitIsNonMutating(GameTestHelper helper, Villager character,
                                                         Map<ResourceKey<ReagentData>, Float> contents,
                                                         float hunger, float thirst) {
        MS14Provider.update(character, MS14Bridges.STOMACH, new ReagentAttachment(contents));
        MS14Provider.update(character, MS14Bridges.HUNGER, new HungerAttachment(new HungerComponent(hunger)));
        MS14Provider.update(character, MS14Bridges.THIRST, new ThirstAttachment(new ThirstComponent(thirst)));

        require(StomachSystem.vomit(vomitContext(character, (ServerLevel) helper.getLevel())) == EffectResult.FAILED,
                "unknown positive stomach reagent makes vomit fail");
        require(MS14Provider.get(character, MS14Bridges.STOMACH).getMap().equals(contents),
                "failed vomit preserves the complete stored stomach mixture");
        require(MS14Provider.get(character, MS14Bridges.HUNGER).hunger() == hunger
                        && MS14Provider.get(character, MS14Bridges.THIRST).thirst() == thirst,
                "failed vomit does not apply hunger or thirst penalties");
        require(!character.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "failed vomit does not create or apply status effects");
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vomitDeadEligibleTargetDoesNotAttachStomachOrStatus(GameTestHelper helper) {
        Villager character = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        character.setHealth(0f);
        require(StomachSystem.vomit(vomitContext(character, (ServerLevel) helper.getLevel()))
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "dead eligible target is unsupported");
        require(!character.hasData(ModDataAttachments.STOMACH.get()),
                "dead target does not materialize an empty stomach attachment");
        require(!character.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "dead target does not materialize an empty status attachment");
        helper.succeed();
    }

    private static EffectContext vomitContext(net.minecraft.world.entity.Entity entity, ServerLevel level) {
        return new EffectContext(level, entity, 1f, RandomSource.create(1L), ConditionContext.unavailable(),
                EffectCause.MANUAL);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
