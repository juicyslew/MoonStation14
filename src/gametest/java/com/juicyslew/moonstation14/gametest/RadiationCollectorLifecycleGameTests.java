package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.radiation.ModRadiationCollectors;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"collector-lifecycle"})
public final class RadiationCollectorLifecycleGameTests {
    private RadiationCollectorLifecycleGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void suspensionAndReloadPreserveInstalledFiniteTankAndOwner(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 128, 2));
        helper.setBlock(new BlockPos(2, 128, 2), ModBlocks.RADIATION_COLLECTOR.get());
        var original = (RadiationCollectorBlockEntity) helper.getLevel().getBlockEntity(pos);
        original.selectProfile(ModRadiationCollectors.FULL_TANK_ID);
        var placementIdentity = original.identity();
        var before = original.tankSlot().snapshot();
        var contents = GasTankItem.contents(before.stack(), helper.getLevel()).orElseThrow();
        double plasma = contents.mixture().moles(GasType.PLASMA);
        var saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
        original.setRemoved(); // Models the BE suspension performed by chunk unload, not block removal.

        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        var restored = (RadiationCollectorBlockEntity) helper.getLevel().getBlockEntity(pos);
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        var after = restored.tankSlot().snapshot();
        var restoredContents = GasTankItem.contents(after.stack(), helper.getLevel()).orElseThrow();
        check(before.owner().equals(after.owner()), "slot owner UUID survives suspension reload");
        check(placementIdentity.equals(restored.identity()), "machine placement identity survives suspension reload");
        check(before.token().equals(after.token()), "canonical tank token survives suspension reload");
        check(plasma == restoredContents.mixture().moles(GasType.PLASMA), "finite plasma payload survives without refill");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"collector_interaction"})
    public static void toggleDelayRejectsEarlyUseAndSurvivesSuspensionReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 128, 2));
        level.setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        var collector = (RadiationCollectorBlockEntity) level.getBlockEntity(pos);
        collector.selectProfile(ModRadiationCollectors.EMPTY_ID);
        long started = level.getGameTime();
        check(collector.toggleEnabled(), "first toggle is accepted immediately");
        check(collector.enabled(), "accepted first toggle changes enabled state");
        var initialSave = collector.saveWithFullMetadata(level.registryAccess());
        check(initialSave.getLong("nextToggleTime") == started + 20, "resolved profile sets a 20-tick deadline");

        helper.runAtTickTime(5, () -> {
            check(!collector.toggleEnabled(), "toggle at T+5 is rejected");
            check(collector.enabled(), "rejected toggle leaves enabled state unchanged");
            check(collector.saveWithFullMetadata(level.registryAccess()).getLong("nextToggleTime") == started + 20,
                    "rejected toggle does not extend deadline");

            var saved = collector.saveWithFullMetadata(level.registryAccess());
            collector.setRemoved(); // Chunk suspension semantics: retain installed machine state.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
            var restored = (RadiationCollectorBlockEntity) level.getBlockEntity(pos);
            restored.loadWithComponents(saved, level.registryAccess());
            helper.runAtTickTime(19, () -> {
                check(!restored.toggleEnabled(), "toggle at T+19 after reload is rejected");
                check(restored.enabled(), "reload and rejection preserve enabled state");
                check(restored.saveWithFullMetadata(level.registryAccess()).getLong("nextToggleTime") == started + 20,
                        "reload preserves original deadline without extension");
            });
            helper.runAtTickTime(20, () -> {
                check(restored.toggleEnabled(), "toggle at exact T+20 deadline is accepted");
                check(!restored.enabled(), "accepted deadline toggle changes enabled state");
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void permanentReplacementDisposesTankButStateChangeDoesNot(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 128, 2));
        helper.setBlock(new BlockPos(2, 128, 2), ModBlocks.RADIATION_COLLECTOR.get());
        var collector = (RadiationCollectorBlockEntity) helper.getLevel().getBlockEntity(pos);
        var tank = collector.tankSlot().snapshot();
        helper.getLevel().setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        check(!tank.stack().isEmpty() && !collector.tankSlot().snapshot().stack().isEmpty(), "same-block state change retains tank");
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        check(collector.tankSlot().snapshot().stack().isEmpty(), "permanent replacement deletes installed tank");
        check(collector.tankSlot().snapshot().token() == null, "permanent replacement retires canonical claim");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void fullProfileCollectorDropCannotDuplicateStarterTank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 128, 2));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.RADIATION_COLLECTOR.get().defaultBlockState(), 3);
        var collector = (RadiationCollectorBlockEntity) level.getBlockEntity(pos);
        collector.selectProfile(ModRadiationCollectors.FULL_TANK_ID);
        check(!collector.tankSlot().snapshot().stack().isEmpty(), "full profile resolves its starter tank before breaking");
        var actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "collector-lifecycle"));
        actor.setGameMode(GameType.SURVIVAL);
        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        check(actor.gameMode.destroyBlock(pos), "normal survival block break succeeds");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        ItemStack droppedCollector = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(ModBlocks.RADIATION_COLLECTOR.get().asItem())).findFirst().orElse(ItemStack.EMPTY);
        check(!droppedCollector.isEmpty(), "ordinary destruction drops collector block item");
        var blockData = droppedCollector.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        check(blockData != null && !blockData.copyTag().contains("tankSlot"), "block item excludes private installed tank");
        check(blockData.copyTag().getBoolean("initialized"), "block item cannot reinitialize profile starter tank");
        check(blockData.copyTag().getString("profile").equals(ModRadiationCollectors.FULL_TANK_ID.toString()),
                "dropped block item preserves the selected full-tank profile");
        check(drops.stream().noneMatch(entity -> GasTankItem.contents(entity.getItem(), helper.getLevel()).isPresent()),
                "owner deletion does not eject a second tank item");

        actor.setItemInHand(InteractionHand.MAIN_HAND, droppedCollector.copy());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
        var placeResult = ModBlocks.RADIATION_COLLECTOR.get().asItem().useOn(
                new net.minecraft.world.item.context.UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
        check(placeResult.consumesAction(), "actual dropped collector item places through BlockItem path");
        var replacement = (RadiationCollectorBlockEntity) level.getBlockEntity(pos);
        check(replacement != null, "replacement collector block entity exists");
        check(replacement.saveWithFullMetadata(level.registryAccess()).getString("profile")
                        .equals(ModRadiationCollectors.FULL_TANK_ID.toString()),
                "placing the dropped block item restores the selected full-tank profile");
        check(replacement.tankSlot().snapshot().stack().isEmpty(), "placed full-profile drop remains without starter tank");
        check(level.getBlockState(pos).is(ModBlocks.RADIATION_COLLECTOR.get()), "drop placement restores collector block");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
