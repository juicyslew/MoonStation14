package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyPlacementCollisionGameTests {
    private BodyPlacementCollisionGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void selectedLaneAndNewNeighborArmOnly(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos neighbor = target.west();
        level.setBlock(neighbor, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity visual = (VisualPipeBlockEntity) level.getBlockEntity(neighbor);
        require(visual != null && visual.setLayersForPlacement(VisualPipeBlockEntity.SECONDARY), "secondary neighbor setup");

        // The new arm occupies the east end of the existing pipe, away from its center.
        AABB neighborArm = box(neighbor, .80, .13, .13, .85, .14, .14);
        require(BodyPlacementCollision.pipeIntersects(level, neighborArm, target, VisualPipeBlockEntity.SECONDARY), "new neighbor arm blocks");
        require(!BodyPlacementCollision.pipeIntersects(level, neighborArm, target, VisualPipeBlockEntity.TERTIARY), "unrelated lane does not block");
        require(!BodyPlacementCollision.pipeIntersects(level, box(neighbor, .80, .125, .13, .85, .125, .14),
                target, VisualPipeBlockEntity.SECONDARY), "touching face allowed");

        level.setBlock(target, ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(), 3);
        VisualPipeBlockEntity crossing = (VisualPipeBlockEntity) level.getBlockEntity(target);
        require(crossing != null && crossing.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY), "crossing setup");
        require(BodyPlacementCollision.pipeIntersects(level, box(target, .13, .13, .13, .14, .14, .14),
                target, VisualPipeBlockEntity.SECONDARY), "secondary crossing center blocks");
        require(!BodyPlacementCollision.pipeIntersects(level, box(target, .5, .5, .5, .51, .51, .51),
                target, VisualPipeBlockEntity.SECONDARY), "existing primary center is not newly added");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void deviceUsesCollisionNotPipeOutline(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        var state = ModBlocks.GAS_VENT.get().defaultBlockState();
        var shape = state.getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
        require(!BodyPlacementCollision.intersects(shape, pos, box(pos, .4, .4, .4, .6, .6, .6)), "noncolliding vent does not block device placement");
        var solidShape = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                .getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
        require(BodyPlacementCollision.intersects(solidShape, pos, box(pos, .4, .4, .4, .6, .6, .6)), "solid block collision blocks device placement");
        require(!BodyPlacementCollision.intersects(shape, pos, box(pos, 1, .4, .4, 1.1, .6, .6)), "device face touch allowed");
        helper.succeed();
    }

    private static AABB box(BlockPos pos, double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1,
                pos.getX() + x2, pos.getY() + y2, pos.getZ() + z2);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
