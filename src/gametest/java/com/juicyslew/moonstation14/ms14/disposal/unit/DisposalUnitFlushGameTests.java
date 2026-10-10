package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;

@com.juicyslew.moonstation14.gametest.support.GameTestTags({"disposal", "containment"})
@PrefixGameTestTemplate(false)
public final class DisposalUnitFlushGameTests {
    private DisposalUnitFlushGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 30)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"hands"})
    public static void containedHandDropAppendsToUnitPayloadStore(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        BlockPos expectedBodyPos = helper.absolutePos(new BlockPos(5, 2, 3));
        var body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(5, 2, 3));
        require(body.isAddedToLevel() && !body.isRemoved() && level.getEntity(body.getUUID()) == body
                        && body.blockPosition().equals(expectedBodyPos),
                "body spawned inside fixture at " + expectedBodyPos + ", actual=" + body.blockPosition()
                        + ", added=" + body.isAddedToLevel() + ", exactEntity=" + (level.getEntity(body.getUUID()) == body));
        long revision = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess
                .holdItem(body, "left", "unit-drop", new ItemStack(Items.DIAMOND, 3));
        require(unit.insertBody(body), "body admitted to real disposal unit");
        var resolvedUnit = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals()
                .get(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.UNIT);
        var unitView = unit.view();
        require(resolvedUnit != null && unitView.ownerIdentity().equals(unit.identity())
                        && unitView.worldTarget().filter(pos::equals).isPresent()
                        && unitView.cameraCollision() == resolvedUnit.containedView().cameraCollision()
                        && unitView.cameraCollision(),
                "live unit view publishes its identity, target, and resolved collision default");
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransferGameTestAccess
                .dropOwned(body, "left", "unit-drop", revision)
                == com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS,
                "contained drop commits to unit store");
        var payloads = unit.contents().snapshot();
        require(payloads.size() == 1 && payloads.values().iterator().next().stack().getCount() == 3,
                "unit store owns the whole stack");
        require(body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())
                .token("left").isEmpty(), "source hand cleared");
        require(level.getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "no world item duplicated");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    public static void firstActionUsesCurrentWorldClock(GameTestHelper helper) {
        helper.runAtTickTime(5, () -> {
            var level = helper.getLevel();
            BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
            level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
            var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
            require(unit != null && unit.contents() != null && unit.reservoirAir() != null,
                    "runtime contents and empty reservoir initialize on immediate post-placement access");
            unit.setGameTestPowerOverride(true);
            unit.engage();
            var timing = unit.timingSnapshot();
            require(timing.logicalTick() == level.getGameTime(), "new kernel starts at current level game time");
            require(timing.nextFlushTick() != null && timing.nextFlushTick() > level.getGameTime(), "first manual deadline is scheduled in the future");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 20)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"power"})
    public static void stalePoweredBlockStateDoesNotPowerUnitKernel(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState()
                .setValue(DisposalUnitBlock.POWERED, true), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        unit.setGameTestPowerOverride(null);
        DisposalUnitBlockEntity.serverTick(level, pos, level.getBlockState(pos), unit);
        require(!unit.timingSnapshot().powered(), "unknown or stale receiver meter overrides cached powered visual");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 80)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"power"})
    public static void missingTrunkAndPowerLossRetainOwnedItem(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(3, 2, 3), pos = helper.absolutePos(local);
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        unit.setGameTestPowerOverride(true);
        var item = new ItemEntity(level, pos.getX() + 1.5, pos.getY() + .2, pos.getZ() + .5, new ItemStack(Items.STICK));
        require(level.addFreshEntity(item), "world item entity added to level");
        helper.runAfterDelay(1, () -> {
        awaitWorldItemReady(helper, item, "missing-trunk item admission");
        require(unit.acceptWorldItem(item), "world item admitted");
        unit.engage();
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeAtTickTime(helper, 45, () -> {
            require(unit.contents().snapshot().size() == 1, "missing same-floor trunk preserves payload");
            unit.setGameTestPowerOverride(false); unit.engage();
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeAtTickTime(helper, 70, () -> {
                require(unit.contents().snapshot().size() == 1, "power loss preserves payload");
                helper.succeed();
            });
        });
        });
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 100)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"power"})
    public static void poweredFlushTransfersWholeItemThroughOpenTrunkToHolder(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(3, 2, 3), pos = helper.absolutePos(local), entry = pos.below();
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
        var world = DisposalTubeWorldData.forMutation(level);
         level.setBlock(entry, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
         require(world.add(level, new DisposalTubeRecord(UUID.randomUUID(), entry, ModDisposals.id("disposal_trunk").toString(), Direction.DOWN, true, "", "", 0), p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())), "add open-end trunk record on real tube block");
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        unit.setGameTestPowerOverride(true);
        var body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, local);
        require(unit.insertBody(body), "body admitted to exact unit owner");
        var item = new ItemEntity(level, pos.getX() + 1.5, pos.getY() + .2, pos.getZ() + .5, new ItemStack(Items.STICK, 3));
        require(level.addFreshEntity(item), "world item entity added to level");
        helper.runAfterDelay(1, () -> {
        awaitWorldItemReady(helper, item, "flush item admission");
        require(unit.acceptWorldItem(item), "whole stack admitted");
        unit.engage();
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeAtTickTime(helper, 75, () -> {
            require(unit.contents().snapshot().isEmpty(), "unit clears only after holder payload admission");
            require(unit.bodyIds().isEmpty() && !body.isRemoved(), "contained body ownership transferred without discarding body");
            helper.succeed();
        });
        });
    }

    @GameTest(templateNamespace = "moonstation14", batch = "disposals", template = "disposal_clear", timeoutTicks = 40)
    @com.juicyslew.moonstation14.gametest.support.GameTestTags({"atmosphere"})
    public static void failedReservoirReserveCannotLeaveHolderOrLoseUnitOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3)), entry = pos.below();
        level.setBlock(pos, com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get().defaultBlockState(), 3);
         level.setBlock(entry, ModBlocks.DISPOSAL_TUBE.get().defaultBlockState(), 3);
         require(DisposalTubeWorldData.forMutation(level).add(level,
                 new DisposalTubeRecord(UUID.randomUUID(), entry, ModDisposals.id("disposal_trunk").toString(), Direction.DOWN, true, "", "", 0), p -> level.getBlockState(p).is(ModBlocks.DISPOSAL_TUBE.get())),
                "open trunk registered");
        var unit = (DisposalUnitBlockEntity) level.getBlockEntity(pos);
        unit.setGameTestPowerOverride(true);
        var body = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 2, 3));
        var item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5, new ItemStack(Items.STICK, 2));
        level.addFreshEntity(item);
        awaitBodyReady(helper, body, 0, () -> {
        require(unit.insertBody(body), "body admitted before handoff");
        awaitWorldItemReady(helper, item, "failed-reserve item admission", 0, () -> {
        require(unit.acceptWorldItem(item), "whole stack admitted before handoff");
        var entriesBefore = unit.contents().snapshot();
        var bodiesBefore = unit.bodyIds();
        int holdersBefore = level.getEntitiesOfClass(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder.class,
                new net.minecraft.world.phys.AABB(pos).inflate(8)).size();
        unit.failNextReservoirHandoffForTest();
        require(!unit.attemptFlushForGameTest(), "failed reservoir compare-and-replace rejects flush before ownership transfer");
        var expectedGas = unit.failedHandoffReservoirSnapshotForTest();
        var actualGas = unit.reservoirAir().sample().orElseThrow().mixture();
        require(expectedGas != null && expectedGas.gasMoles().equals(actualGas.gasMoles())
                        && expectedGas.temperatureKelvin() == actualGas.temperatureKelvin(),
                "failed reserve retains the complete sampled unit gas inventory");
        require(unit.contents().snapshot().keySet().equals(entriesBefore.keySet()) && unit.bodyIds().equals(bodiesBefore)
                        && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(body).orElse(null) == unit,
                "failed reserve leaves all item and body ownership at the unit");
        require(level.getEntitiesOfClass(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder.class,
                new net.minecraft.world.phys.AABB(pos).inflate(8)).size() == holdersBefore,
                "failed reserve spawned no live holder");
        helper.succeed();
        });
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void awaitWorldItemReady(GameTestHelper helper, ItemEntity item, String operation) {
        awaitWorldItemReady(helper, item, operation, 0, () -> { });
    }

    private static void awaitWorldItemReady(GameTestHelper helper, ItemEntity item, String operation, int waited, Runnable ready) {
        var level = helper.getLevel();
        var chunk = level.getChunkSource().getChunkNow(item.chunkPosition().x, item.chunkPosition().z);
        boolean full = chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        boolean exact = level.getEntity(item.getUUID()) == item;
        if (item.isAlive() && !item.isRemoved() && item.isAddedToLevel() && exact && full) { ready.run(); return; }
        if (waited < 10) { com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> awaitWorldItemReady(helper, item, operation, waited + 1, ready)); return; }
        throw new GameTestAssertException(operation + " prerequisites failed after 10 ticks: alive=" + item.isAlive()
                + ", removed=" + item.isRemoved() + ", added=" + item.isAddedToLevel()
                + ", chunk=" + item.chunkPosition() + ", chunkStatus="
                + (chunk == null ? "missing" : chunk.getFullStatus()) + ", exactUuidLookup=" + exact
                + ", ownerLevel=" + item.level());
    }

    private static void awaitBodyReady(GameTestHelper helper, net.minecraft.world.entity.Entity body, int waited, Runnable ready) {
        var level = helper.getLevel();
        var chunk = level.getChunkSource().getChunkNow(body.chunkPosition().x, body.chunkPosition().z);
        boolean full = chunk != null && chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL);
        boolean exact = level.getEntity(body.getUUID()) == body;
        if (body.isAddedToLevel() && body.isAlive() && !body.isRemoved() && exact && full) { ready.run(); return; }
        if (waited < 10) { com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> awaitBodyReady(helper, body, waited + 1, ready)); return; }
        throw new GameTestAssertException("failed-reserve body readiness timeout: added=" + body.isAddedToLevel()
                + ", alive=" + body.isAlive() + ", removed=" + body.isRemoved() + ", exactUuidLookup=" + exact
                + ", chunk=" + body.chunkPosition() + ", chunkStatus=" + (chunk == null ? "missing" : chunk.getFullStatus()));
    }
}
