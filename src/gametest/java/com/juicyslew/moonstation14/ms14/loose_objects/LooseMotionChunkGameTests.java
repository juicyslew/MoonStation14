package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.entities.LooseChunkBounds;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Boundary regressions for the chunk eligibility bounds used by loose motion. */
public final class LooseMotionChunkGameTests {
    private LooseMotionChunkGameTests() { }

    public static void exclusiveSweepBoundary(GameTestHelper helper) {
        var exact = LooseChunkBounds.from(new AABB(0, 1, 0, 16, 2, 16));
        var crossing = LooseChunkBounds.from(new AABB(0, 1, 0, Math.nextUp(16.0), 2, 16));
        var negative = LooseChunkBounds.from(new AABB(-32, 1, -32, -16, 2, -16));
        if (exact.minX() != 0 || exact.maxX() != 0 || exact.minZ() != 0 || exact.maxZ() != 0
                || crossing.maxX() != 1 || negative.minX() != -2 || negative.maxX() != -2
                || negative.minZ() != -2 || negative.maxZ() != -2)
            throw new GameTestAssertException("swept AABB chunk eligibility respects exclusive boundaries");
        helper.succeed();
    }

    public static void unloadedNeighborMotion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int cx = (origin.getX() >> 4) + 128, cz = (origin.getZ() >> 4) + 128;
        level.getChunk(cx, cz);
        require(level.getChunkSource().getChunkNow(cx + 1, cz) == null, "neighbor initially unloaded");
        int x = cx * 16;
        double y = origin.getY() + 20;
        LooseItemEntity clear = LooseItemEntity.create(level, new Vec3(x + 15.3, y, cz * 16 + 8), new ItemStack(Items.STICK));
        clear.launch(new Vec3(.1, 0, 0), 0);
        Vec3 clearStart = clear.position();
        clear.tick();
        require(clear.position().x > clearStart.x && clear.position().y < clearStart.y, "motion proceeds without loading neighbor");
        require(level.getChunkSource().getChunkNow(cx + 1, cz) == null, "clear sweep leaves neighbor unloaded");

        LooseItemEntity crossing = LooseItemEntity.create(level, new Vec3(x + 15.65, y, cz * 16 + 8), new ItemStack(Items.STICK));
        crossing.launch(new Vec3(.2, 0, 0), 0);
        Vec3 crossingStart = crossing.position();
        crossing.tick();
        require(crossing.motionBlockedByUnloaded() && crossing.position().equals(crossingStart)
                && crossing.getDeltaMovement().lengthSqr() > 0, "crossing sweep is deferred without losing motion");
        level.getChunk(cx + 1, cz);
        crossing.tick();
        require(!crossing.motionBlockedByUnloaded() && crossing.position().x > crossingStart.x
                && crossing.position().y < crossingStart.y, "motion resumes when neighbor is loaded");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
