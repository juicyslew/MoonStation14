package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Body-safe BlockPlaceContext regression: no Player/carrier is used for placement geometry. */
@GameTestTags({"hands", "interaction", "device-network", "device-network-owner"})
@PrefixGameTestTemplate(false)
public final class BodyBlockItemPlacementGameTests {
    private BodyBlockItemPlacementGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void controlledMobUsesBodyEyePlacementContext(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setYRot(90);
        body.setXRot(-55);
        helper.runAfterDelay(1, () -> {
            body.setYRot(90);
            body.setXRot(-55);
            BlockPos clicked = body.blockPosition().south(2);
            BlockPos dest = clicked.relative(Direction.NORTH);
            helper.getLevel().setBlock(clicked, Blocks.STONE.defaultBlockState(), 3);
            var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false);
            var stack = new ItemStack(ModBlocks.STATION_FLOOR.get().asItem());
            var nearest = Direction.orderedByNearest(body);
            Direction expectedVertical = Direction.UP;
            for (Direction direction : nearest) if (direction.getAxis().isVertical()) {
                expectedVertical = direction;
                break;
            }
            var context = BodyBlockItemPlacement.placementContext(body, helper.getLevel(), stack, hit,
                    clicked, dest, Direction.NORTH, true);
            if (context.getPlayer() != null || context.getClickedPos() != dest
                    || !context.isSecondaryUseActive() || context.getRotation() != body.getYRot()
                    || context.getNearestLookingDirections()[0] != Direction.SOUTH
                    || context.getNearestLookingDirection() != Direction.orderedByNearest(body)[0]
                    || context.getNearestLookingVerticalDirection() != expectedVertical
                    || context.getHorizontalDirection() != body.getDirection())
                throw new GameTestAssertException("context uses controlled body pose, target, and sneak without a player");
            var placed = ModBlocks.STATION_FLOOR.get().getStateForPlacement(context);
            if (placed == null) throw new GameTestAssertException("supported existing floor block resolves placement state for a controlled mob");
            var replacing = BodyBlockItemPlacement.placementContext(body, helper.getLevel(), stack, hit,
                    clicked, clicked, Direction.NORTH, false);
            if (replacing.getPlayer() != null || replacing.getClickedPos() != clicked
                    || replacing.getNearestLookingDirection() != nearest[0]
                    || replacing.getNearestLookingVerticalDirection() != expectedVertical)
                throw new GameTestAssertException("replacing-click context preserves body-nearest direction ordering");
            BlockPos obstructed = dest.east();
            helper.getLevel().setBlock(obstructed, Blocks.STONE.defaultBlockState(), 3);
            var obstructionContext = BodyBlockItemPlacement.placementContext(body, helper.getLevel(), stack,
                    new BlockHitResult(Vec3.atCenterOf(obstructed), Direction.UP, obstructed, false),
                    obstructed, obstructed, Direction.UP, false);
            if (helper.getLevel().getBlockState(obstructed).canBeReplaced(obstructionContext))
                throw new GameTestAssertException("existing occupied-block preflight rejects a solid destination");
            assertWallControlPlacement(helper, body, new BlockPos(5, 1, 5), ModBlocks.SIGNAL_BUTTON.get());
            assertWallControlPlacement(helper, body, new BlockPos(8, 1, 5), ModBlocks.SIGNAL_SWITCH.get());
            helper.succeed();
        });
    }

    private static void assertWallControlPlacement(GameTestHelper helper, Villager body, BlockPos host,
            net.minecraft.world.level.block.Block control) {
        helper.getLevel().setBlock(host, Blocks.STONE.defaultBlockState(), 3);
        BlockPos target = host.north();
        var hit = new BlockHitResult(Vec3.atCenterOf(host), Direction.NORTH, host, false);
        var context = BodyBlockItemPlacement.placementContext(body, helper.getLevel(),
                new ItemStack(control.asItem()), hit, host, target, Direction.NORTH, false);
        var state = control.getStateForPlacement(context);
        if (state == null || !state.canSurvive(helper.getLevel(), target)
                || state.getValue(com.juicyslew.moonstation14.ms14.devicenet.control.WallSignalControlBlock.FACING)
                != Direction.NORTH)
            throw new GameTestAssertException("wall control uses body-safe context and stages on its solid host: " + control);
    }
}
