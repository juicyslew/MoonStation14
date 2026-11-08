package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.ProduceFactory;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsFixtureTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@PrefixGameTestTemplate(false)
@GameTestTags({"item-processing"})
public final class HeldItemConversionAuthenticatedGameTests {
    private HeldItemConversionAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100, batch = "catalog-item-processing-held-conversion")
    @GameTestTags({"botany-in-hand-conversion", "item-processing", "network", "merge-main-remaining", "merge-main-root-causes"})
    public static void registeredHeldTomatoConvertsThroughAuthenticatedUse(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "held-conversion-v1");
        var manager = PrototypeRuntime.serverManager();
        var processingType = ModItemProcessingProfiles.TYPE;
        var toolType = ModToolProfiles.TYPE;
        var ingestionType = ModIngestionProfiles.TYPE;
        var oldProcessing = new HashMap<>(manager.snapshot(processingType).asMap());
        var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
        var oldIngestion = new HashMap<>(manager.snapshot(ingestionType).asMap());
        var finished = new AtomicBoolean();
        var cleanup = new ItemProcessingFixtureCleanup(helper, fixture, finished, manager,
                processingType, oldProcessing, toolType, oldTools, ingestionType, oldIngestion, null);
        try {
            ResourceLocation tomato = BuiltInRegistries.ITEM.getKey(ModItems.TOMATO.get());
            ResourceLocation meat = BuiltInRegistries.ITEM.getKey(ModItems.FOOD_MEAT_WHEAT.get());
            ResourceLocation cocoa = BuiltInRegistries.ITEM.getKey(ModItems.FOOD_COCOA_BEANS.get());
            var replacement = new HashMap<>(oldProcessing);
            replacement.put(tomato, new ItemProcessingProfile(null, null, null,
                    new ItemProcessingProfile.SpawnItemsOnUse(java.util.List.of(
                            new ItemProcessingProfile.ItemOutput(meat, 2),
                            new ItemProcessingProfile.ItemOutput(cocoa, 1)), 1,
                    BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.BOTANY_PRODUCE_SLIME_SQUISH.get()))));
            manager.publishDecoded(processingType, replacement);
            require(manager.snapshot(processingType).asMap().get(tomato).spawnItemsOnUse().outputs().equals(
                    replacement.get(tomato).spawnItemsOnUse().outputs()), "scoped tomato conversion profile is resolved after publication");

            helper.runAtTickTime(2, () -> {
            try {
            if (!cleanup.isOpen()) return;
            var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
            var body = active.body();
            // Keep the native carrier out of range: delivery must follow the committed body/camera.
            body.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getX() + .5,
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getY(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)).getZ() + .5);
            fixture.player().setPos(helper.absolutePos(new net.minecraft.core.BlockPos(40, 1, 40)).getX(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(40, 1, 40)).getY(),
                    helper.absolutePos(new net.minecraft.core.BlockPos(40, 1, 40)).getZ());
            fixture.player().setCamera(body);
            var cueId = replacement.get(tomato).spawnItemsOnUse().sound();
            var cueEvent = BuiltInRegistries.SOUND_EVENT.get(cueId);
            require(cueEvent != null && cueId.equals(BuiltInRegistries.SOUND_EVENT.getKey(cueEvent)), "resolved conversion cue exists");
            var sounds = new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
            var hands = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            String hand = hands.activeHand();
            String other = hands.handIds().stream().filter(id -> !id.equals(hand)).findFirst().orElseThrow();
            var seedEntry = ModSeedProfiles.serverCatalog().asMap().entrySet().stream()
                    .filter(entry -> entry.getValue().product().equals(tomato)).findFirst().orElseThrow();
            var source = ProduceFactory.createHarvest(new com.juicyslew.moonstation14.ms14.botany.PlantSeedReference(
                    new com.juicyslew.moonstation14.ms14.botany.GeneticValue(seedEntry.getKey(), seedEntry.getValue()), true),
                    seedEntry.getValue().lifecycle().potency());
            var sourceReagents = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(source,
                    com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits();
            require(!sourceReagents.isEmpty() && source.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) != null,
                    "harvested tomato source carries real genetics and reagents");
            require(LiveHandsFixtureTransfer.put(fixture.player(), body, hand, "conversion-source", source), "source receives ordinary live hand ownership");
            require(LiveHandsFixtureTransfer.put(fixture.player(), body, other, "conversion-paper", new ItemStack(net.minecraft.world.item.Items.PAPER)),
                    "other hand is occupied through live hand owner");

            var before = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(active.epoch(), 2));
            var held = before.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
            var request = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,
                    hand, active.epoch(), 3, before.revision(), null, held.token());
            drainConversionSounds(fixture, sounds, cueEvent);
            var conversionResult = BodyHandRequestService.apply(fixture.player(), request);
            require(conversionResult.accepted(), "authenticated ordinary USE_IN_HAND converts the held source immediately");
            var after = (LiveHands) body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(after.token(hand).isPresent() && after.stackCopy(hand).orElseThrow().is(ModItems.FOOD_COCOA_BEANS.get()),
                    "last output occupies the freed source hand");
            require(after.stackCopy(other).orElseThrow().is(net.minecraft.world.item.Items.PAPER), "other hand remains unchanged");
            require(after.token(hand).filter(token -> !token.equals(held.token())).isPresent(), "source ownership token was replaced");
            var area = new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5);
            var outputEntities = ((net.minecraft.server.level.ServerLevel) body.level()).getEntitiesOfClass(LooseItemEntity.class, area,
                    entity -> entity.getItem().is(ModItems.FOOD_MEAT_WHEAT.get()) || entity.getItem().is(ModItems.FOOD_COCOA_BEANS.get()));
            require(outputEntities.size() == 2 && outputEntities.stream().allMatch(entity ->
                    entity.getItem().is(ModItems.FOOD_MEAT_WHEAT.get()) && entity.getItem().getCount() == 1),
                    "earlier two-meat output is published as two independent count-one stacks");
            drainConversionSounds(fixture, sounds, cueEvent);
            require(outputEntities.getFirst().getItem().getCount() == 1, "accepted conversion has published its fresh outputs before cue validation");
            require(sounds.size() == 1, "successful conversion sends exactly one resolved native cue");
            var heard = sounds.getFirst();
            require(heard.getSource() == net.minecraft.sounds.SoundSource.PLAYERS && heard.getSound().value() == cueEvent,
                    "conversion cue event and PLAYERS source are preserved");
            require(heard.getVolume() == 1f && heard.getPitch() == 1f, "conversion cue volume and pitch are preserved");
            var expectedCue = new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.Holder.direct(cueEvent), net.minecraft.sounds.SoundSource.PLAYERS,
                    body.getX(), body.getY(), body.getZ(), 1f, 1f, 0L);
            require(heard.getX() == expectedCue.getX() && heard.getY() == expectedCue.getY() && heard.getZ() == expectedCue.getZ(),
                    "conversion cue packet uses committed body position");
            require(after.stackCopy(hand).orElseThrow().getCount() == 1, "held cocoa output is count one");
            require(outputEntities.stream().allMatch(entity -> entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) == null
                    && entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == null
                    && entity.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null),
                    "world conversion output does not inherit source genetics or produce instance");
            var heldOutput = after.stackCopy(hand).orElseThrow();
            require(heldOutput.get(com.juicyslew.moonstation14.component.ModDataComponents.GENETIC_VALUE.get()) == null
                            && heldOutput.get(com.juicyslew.moonstation14.component.ModDataComponents.PLANT_SEED_REFERENCE.get()) == null
                            && heldOutput.get(com.juicyslew.moonstation14.component.ModDataComponents.PRODUCE_INSTANCE.get()) == null,
                    "held output does not inherit source genetics or produce instance");
            var cocoaSolution = PrototypeRuntime.serverItemFoodSolutionProfiles().get(
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "food_cocoa_beans"));
            var outputReagents = com.juicyslew.moonstation14.ms14.MS14Provider.getDetached(heldOutput,
                    com.juicyslew.moonstation14.ms14.MS14Bridges.REAGENT).snapshotUnits();
            var outputContents = outputReagents.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    entry -> entry.getKey().location(), java.util.Map.Entry::getValue));
            require(cocoaSolution != null && outputContents.equals(cocoaSolution.contentsCents()),
                    "held output chemistry comes from its own resolved food solution profile, not source chemistry");
            var sourceContents = sourceReagents.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    entry -> entry.getKey().location(), java.util.Map.Entry::getValue));
            require(!outputContents.equals(sourceContents), "held output chemistry differs from harvested source chemistry");
            var replay = BodyHandRequestService.apply(fixture.player(), request);
            require(!replay.accepted(), "exact accepted request cannot replay after commit");
            drainConversionSounds(fixture, sounds, cueEvent);
            require(sounds.size() == 1, "replay emits no additional conversion cue");
            var finalOutputs = ((net.minecraft.server.level.ServerLevel) body.level()).getEntitiesOfClass(LooseItemEntity.class, area,
                    entity -> !entity.isRemoved());
            var initialOutputIds = outputEntities.stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            require(finalOutputs.size() == 2 && finalOutputs.stream().allMatch(entity ->
                    entity.getItem().is(ModItems.FOOD_MEAT_WHEAT.get()) && entity.getItem().getCount() == 1)
                    && finalOutputs.stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet()).equals(initialOutputIds),
                    "replay creates no extra output and preserves the two original world output identities");
            require(after.stackCopy(hand).orElseThrow().is(ModItems.FOOD_COCOA_BEANS.get())
                    && after.stackCopy(hand).orElseThrow().getCount() == 1, "replay preserves the single held cocoa output");
            cleanup.finish(null);
            } catch (Throwable failure) { cleanup.finish(failure); }
            });
        } catch (Throwable failure) { cleanup.finish(failure); }
    }

    private static void drainConversionSounds(RegisteredServerPlayerFixture fixture,
            java.util.List<net.minecraft.network.protocol.game.ClientboundSoundPacket> sounds,
            net.minecraft.sounds.SoundEvent expected) {
        Object packet;
        while ((packet = fixture.pollOutbound()) != null)
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound
                    && sound.getSound().value() == expected) sounds.add(sound);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
