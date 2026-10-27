package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.live.BodyLiquidInteraction;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Focused live checks for registered secondary materials, resolved ingestion sources and Log outputs. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-log-cutting", "botany-material-output"})
public final class MaterialOutputSourceGameTests {
    private MaterialOutputSourceGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void registeredMaterialSourcesInitializeOnceAndRemainDepleted(GameTestHelper helper) {
        checkSource(helper, ModItems.COTTON_MATERIAL.get(), Map.of(ModReagents.createKey("fiber"), 500L));
        checkSource(helper, ModItems.PYROTTON_MATERIAL.get(), Map.of(
                ModReagents.createKey("fiber"), 500L, ModReagents.createKey("phlogiston"), 500L));
        helper.succeed();
    }

    private static void checkSource(GameTestHelper helper, net.minecraft.world.item.Item item,
                                    Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> expected) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        var profile = PrototypeRuntime.serverIngestionProfiles().get(id);
        helper.assertTrue(profile != null && profile.edible().requiresSpecialDigestion(), id + " resolves as restricted food");
        var sourceProfile = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(id);
        helper.assertTrue(sourceProfile != null && sourceProfile.capacityCents() == expected.values().stream().mapToLong(Long::longValue).sum(),
                id + " resolves inherited source capacity");
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 30, id + " is a distinct max-30 material");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(stack, helper.getLevel());
        var bridge = ReagentSystem.bridgeForHolder(stack);
        helper.assertTrue(MS14Provider.getDetached(stack, bridge).snapshotUnits().equals(expected), id + " initializes exact resolved chemistry");
        var changed = Map.of(ModReagents.createKey("water"), 125L);
        helper.assertTrue(BodyLiquidInteraction.publishStackReagents(stack, changed), id + " accepts current-solution replacement");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(stack, helper.getLevel());
        helper.assertTrue(MS14Provider.getDetached(stack, bridge).snapshotUnits().equals(changed), id + " does not refill modified source");
        helper.assertTrue(BodyLiquidInteraction.publishStackReagents(stack, Map.of()), id + " can publish empty depleted source");
        com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(stack, helper.getLevel());
        helper.assertTrue(MS14Provider.getDetached(stack, bridge).isEmpty(), id + " remains empty after lazy initialization");
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000, batch = "catalog-item-processing-material-output")
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"merge-main-remaining"})
    public static void registeredSharpLogOutputsCottonAndPyrottonAsFreshUnits(GameTestHelper helper) {
        com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper,
                new ItemStack(ModItems.WHEAT_SEEDS.get()), "material-output", (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var original = new java.util.HashMap<>(manager.snapshot(procType).asMap());
            var type = ModToolProfiles.TYPE;
            var originalTools = new java.util.HashMap<>(manager.snapshot(type).asMap());
            var ingestionType = com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.TYPE;
            var originalIngestion = new java.util.HashMap<>(manager.snapshot(ingestionType).asMap());
            var cleanup = new ItemProcessingFixtureCleanup(h, fixture, finished, manager, procType, original, type, originalTools,
                    ingestionType, originalIngestion, null);
            try {
                var input = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
                var cotton = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.COTTON_MATERIAL.get());
                var pyrotton = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.PYROTTON_MATERIAL.get());
                var processing = new java.util.HashMap<>(original);
                processing.put(input, new ItemProcessingProfile(new ItemProcessingProfile.Log(cotton, 2), null));
                manager.publishDecoded(procType, processing);
                var toolId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.KITCHEN_KNIFE.get());
                var knifeProfile = originalTools.get(toolId);
                require(knifeProfile != null && knifeProfile.sharp() != null && knifeProfile.knife() != null,
                        "real registered KitchenKnife profile retains Sharp and Knife capabilities");
                var body = com.juicyslew.moonstation14.ms14.hands.HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var tool = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, .1, .8),
                        new ItemStack(ModItems.KITCHEN_KNIFE.get()));
                tool.setNoGravity(true); require(level.addFreshEntity(tool), "registered production KitchenKnife published");
                var liveHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(), tool.getUUID(),
                        hand, liveHands.revision(), ignored -> {}) == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                        "authenticated player acquires production KitchenKnife");
                var held = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var token = held.token(hand).orElseThrow();
                long sequence = 1;
                for (var outputId : java.util.List.of(cotton, pyrotton)) {
                    var next = new java.util.HashMap<>(processing);
                    next.put(input, new ItemProcessingProfile(new ItemProcessingProfile.Log(outputId, 2), null));
                    manager.publishDecoded(procType, next);
                    var source = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, .1, 1.5),
                            new ItemStack(ModItems.TOMATO.get()));
                    source.setNoGravity(true); require(level.addFreshEntity(source), "scoped Log input published");
                    body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, source.getBoundingBox().getCenter());
                    var area = new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5);
                    var before = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, area,
                            entity -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(outputId))
                            .stream().map(com.juicyslew.moonstation14.entities.LooseItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
                    var query = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(fixture.player(),
                            new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(epoch, 30 + sequence));
                    var active = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                    require(com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),
                            new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(
                                    com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_TARGET, hand,
                                    epoch, sequence++, query.revision(), source.getUUID(), active.token())).accepted(), "two-unit authenticated Log output succeeds");
                    require(source.isRemoved(), "source removed only after both outputs are published");
                    var outputs = level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, area,
                            entity -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).equals(outputId)
                                    && !before.contains(entity.getUUID()));
                    require(outputs.size() == 2 && outputs.stream().allMatch(entity -> entity.getItem().getCount() == 1),
                            outputId + " creates two distinct new count-one world entities");
                    for (var output : outputs) com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions.initializeResolved(output.getItem(), level);
                    var profile = com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.serverLookup(outputId);
                    var reagentMap = new java.util.LinkedHashMap<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>();
                    profile.contentsCents().forEach((reagent, amount) -> reagentMap.put(
                            net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY, reagent), amount));
                    for (var output : outputs) require(MS14Provider.getDetached(output.getItem(), ReagentSystem.bridgeForHolder(output.getItem()))
                            .snapshotUnits().equals(reagentMap), outputId + " output uses fresh prototype chemistry");
                    require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                            .token(hand).filter(token::equals).isPresent(), "tool remains in hand");
                }
            } catch (Throwable failure) { cleanup.finish(failure); return; }
            cleanup.finish(null);
        });
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
