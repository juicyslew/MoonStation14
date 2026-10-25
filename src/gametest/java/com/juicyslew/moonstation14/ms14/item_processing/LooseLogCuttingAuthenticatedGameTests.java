package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.hands.network.BotanyHarvestAuthenticatedGameTests;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Authenticated live body/active-hand/exact loose-target Log dispatch; profiles are scoped to the fixture. */
@PrefixGameTestTemplate(false)
@GameTestTags({"botany", "botany-log-cutting"})
public final class LooseLogCuttingAuthenticatedGameTests {
    private LooseLogCuttingAuthenticatedGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 4000)
    @GameTestTags({"botany-log-cutting", "botany-log-cutting-authenticated"})
    public static void authenticatedSharpUseCutsExactLooseLogIntoSeparateOutputs(GameTestHelper helper) {
        var seeds = new ItemStack(ModItems.WHEAT_SEEDS.get());
        BotanyHarvestAuthenticatedGameTests.runAuthenticatedHarvest(helper, seeds, "sharp-log-cutting",
                (h, fixture, epoch, hand, tray, pos, level, finished) -> {
            var manager = PrototypeRuntime.serverManager();
            var procType = ModItemProcessingProfiles.TYPE;
            var oldProcessing = new HashMap<>(manager.snapshot(procType).asMap());
            var toolType = ModToolProfiles.TYPE;
            var oldTools = new HashMap<>(manager.snapshot(toolType).asMap());
            try {
                ResourceLocation input = ResourceLocation.withDefaultNamespace("oak_log");
                ResourceLocation wood = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WOOD.get());
                var processing = new HashMap<>(oldProcessing);
                processing.put(input, new ItemProcessingProfile(new ItemProcessingProfile.Log(wood, 2)));
                manager.publishDecoded(procType, processing);
                var tools = new HashMap<>(oldTools);
                ResourceLocation cutter = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.WIRECUTTER.get());
                tools.put(cutter, new ToolProfile(new ToolProfile.Tool(java.util.List.of(ToolProfile.Quality.CUTTING)),
                        null, null, com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        Set.of(), null, null, null, new ToolProfile.Sharp()));
                manager.publishDecoded(toolType, tools);

                var body = HandActorAuthority.resolve(fixture.player()).orElseThrow().body();
                var toolEntity = LooseItemEntity.create(level, body.position().add(0, .1, .8), new ItemStack(ModItems.WIRECUTTER.get()));
                toolEntity.setNoGravity(true); require(level.addFreshEntity(toolEntity), "test-only sharp tool published");
                var hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(BodyHandItemTransfer.pickupSelected(fixture.player(), toolEntity.getUUID(), hand, hands.revision(), ignored -> {})
                        == BodyHandItemTransfer.Result.SUCCESS, "sharp tool acquired through existing hand ownership path");
                hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                var toolToken = hands.token(hand).orElseThrow();
                var log = LooseItemEntity.create(level, body.position().add(0, .1, 1.5), new ItemStack(net.minecraft.world.item.Items.OAK_LOG));
                log.setNoGravity(true); require(level.addFreshEntity(log), "nominated loose source published");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, log.getBoundingBox().getCenter());
                var before = level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                        entity -> entity.getItem().is(ModItems.WOOD.get())).size();
                var query = BodyHandStateService.query(fixture.player(), new BodyHandStateQuery(epoch, 3));
                var active = query.hands().stream().filter(value -> value.id().equals(hand)).findFirst().orElseThrow();
                manager.publishDecoded(procType, oldProcessing);
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 2, query.revision(), hand,
                        active.token(), log)).reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED
                        && !log.isRemoved(), "missing resolved Log profile denies without source mutation");
                manager.publishDecoded(procType, processing);
                var nonSharp = new HashMap<>(tools);
                nonSharp.put(cutter, new ToolProfile(new ToolProfile.Tool(java.util.List.of(ToolProfile.Quality.CUTTING)),
                        null, null, com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata.EMPTY,
                        Set.of(), null, null, null, null));
                manager.publishDecoded(toolType, nonSharp);
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 3, query.revision(), hand,
                        active.token(), log)).reason() == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.DENIED
                        && !log.isRemoved(), "CUTTING-only capability is not Sharp and denies without source mutation");
                manager.publishDecoded(toolType, tools);
                require(!BodyHandRequestService.apply(fixture.player(), request(epoch, 4, query.revision() - 1, hand,
                        active.token(), log)).accepted() && !log.isRemoved(), "stale hand revision denies without source mutation");
                var sourcePosition = log.position();
                log.setPos(body.position().add(0, .1, 7));
                require(!BodyHandRequestService.apply(fixture.player(), request(epoch, 5, query.revision(), hand,
                        active.token(), log)).accepted() && !log.isRemoved(), "out-of-reach nominated source denies without nearest fallback");
                log.setPos(sourcePosition);
                var distractor = LooseItemEntity.create(level, body.position().add(0, .1, 1.0), new ItemStack(net.minecraft.world.item.Items.STICK));
                distractor.setNoGravity(true); require(level.addFreshEntity(distractor), "wrong nominated target published");
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, distractor.getBoundingBox().getCenter());
                require(!BodyHandRequestService.apply(fixture.player(), request(epoch, 6, query.revision(), hand,
                        active.token(), distractor)).accepted() && !log.isRemoved(), "wrong exact nomination does not process the nearer Log");
                distractor.discard();
                body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, log.getBoundingBox().getCenter());
                log.getItem().setCount(2);
                require(!BodyHandRequestService.apply(fixture.player(), request(epoch, 7, query.revision(), hand,
                        active.token(), log)).accepted() && !log.isRemoved() && log.getItem().getCount() == 2,
                        "unsupported multi-unit world source is denied without deleting either unit");
                log.getItem().setCount(1);
                require(BodyHandRequestService.apply(fixture.player(), request(epoch, 8, query.revision(), hand,
                        active.token(), log)).accepted(), "authenticated nominated USE_TARGET accepted");
                require(log.isRemoved(), "whole source entity removed only after output publication");
                require(level.getEntitiesOfClass(LooseItemEntity.class, new net.minecraft.world.phys.AABB(body.position(), body.position()).inflate(5),
                        entity -> entity.getItem().is(ModItems.WOOD.get())).size() == before + 2,
                        "Log count produces two separate world-owned unit outputs");
                var afterHands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                require(afterHands.token(hand).filter(toolToken::equals).isPresent(), "Sharp tool remains in active hand");
            } catch (Throwable failure) {
                finish(h, fixture, finished, failure);
                return;
            } finally {
                manager.publishDecoded(procType, oldProcessing);
                manager.publishDecoded(toolType, oldTools);
            }
            finish(h, fixture, finished, null);
        });
    }

    private static void finish(GameTestHelper helper, com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
                               AtomicBoolean finished, Throwable failure) {
        if (!finished.compareAndSet(false, true)) return;
        try { fixture.close(); } catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        if (failure == null) helper.succeed(); else helper.testInfo.fail(failure);
    }
    private static BodyHandActionRequest request(long epoch, long sequence, long revision, String hand,
            String token, LooseItemEntity target) {
        return new BodyHandActionRequest(BodyHandActionRequest.Action.USE_TARGET, hand, epoch, sequence,
                revision, target.getUUID(), token);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
