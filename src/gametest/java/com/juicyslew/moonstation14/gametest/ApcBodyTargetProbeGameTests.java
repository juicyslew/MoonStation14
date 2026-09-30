package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.power.ui.body.ApcBodyTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApcBodyTargetProbeGameTests {
    private ApcBodyTargetProbeGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void loadedBodyOriginAndRejectsSpoofs(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        var level = helper.getLevel();
        level.setBlock(target, ModBlocks.APC.get().defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "villager created");
        body.setNoAi(true); // fixture only; production NPC movement is untouched
        body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        body.setYRot(-90);
        body.setXRot(20); // eye is above the APC: aim at its face rather than horizontally over it
        body.setYHeadRot(-90);
        require(level.addFreshEntity(body), "body joined server");
        var result = ApcBodyTargetProbe.probe(level, body, target, 4);
        require(result != null && result.pos().equals(target) && result.face() != null,
                "loaded APC hit from live body eye");
        require(ApcBodyTargetProbe.probe(level, body, target.offset(0, 0, 1), 4) == null,
                "expected block must be exact");
        require(ApcBodyTargetProbe.probe(level, body, target, 1) == null, "too far");
        require(ApcBodyTargetProbe.probe(level, body, target, Double.POSITIVE_INFINITY) == null, "unbounded reach");
        body.setYRot(90);
        body.setYHeadRot(90);
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null, "wrong look");
        body.setYRot(-90);
        body.setYHeadRot(-90);
        BlockPos barrier = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null, "occluded");
        level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
        level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4).removeBlockEntity(target);
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null,
                "APC state without a stored BE is not materialized by probe");
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null, "wrong block/BE");
        body.discard();
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null, "removed body");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absentMarginChunkDeniesProbeWithoutLoading(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        // Find a loaded ray chunk with an actually absent chunk in its one-chunk shape-query margin.
        // GameTest may already load the structure's immediate neighbors, so inspect rather than assume.
        int rayX = 0, rayZ = 0, absentX = 0, absentZ = 0;
        boolean found = false;
        for (int dx = -8; dx <= 8 && !found; dx++) {
            for (int dz = -8; dz <= 8 && !found; dz++) {
                int x = (origin.getX() >> 4) + dx, z = (origin.getZ() >> 4) + dz;
                if (level.getChunkSource().getChunkNow(x, z) == null) continue;
                BlockPos candidate = new BlockPos((x << 4) + 10, origin.getY(), (z << 4) + 8);
                if (!level.getChunkSource().getChunkNow(x, z).getBlockState(candidate).isAir()) continue;
                for (int mx = x - 1; mx <= x + 1 && !found; mx++) {
                    for (int mz = z - 1; mz <= z + 1; mz++) {
                        if (level.getChunkSource().getChunkNow(mx, mz) != null) continue;
                        rayX = x;
                        rayZ = z;
                        absentX = mx;
                        absentZ = mz;
                        found = true;
                        break;
                    }
                }
            }
        }
        require(found, "loaded ray chunk with an absent margin chunk exists");
        BlockPos bodyPos = new BlockPos((rayX << 4) + 8, origin.getY(), (rayZ << 4) + 8);
        BlockPos target = bodyPos.offset(2, 0, 0);
        level.setBlock(target, ModBlocks.APC.get().defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "villager created");
        body.setNoAi(true);
        body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        body.setYRot(-90);
        body.setXRot(20);
        body.setYHeadRot(-90);
        require(level.addFreshEntity(body), "body joined server");
        require(level.getChunkSource().getChunkNow(rayX, rayZ) != null
                        && level.getChunkSource().getChunkNow(rayX, rayZ).getBlockEntities().get(target) != null,
                "ray target is a loaded APC with a stored BE");
        require(level.getChunkSource().getChunkNow(absentX, absentZ) == null,
                "margin chunk remains absent after fixture setup");
        require(ApcBodyTargetProbe.probe(level, body, target, 4) == null,
                "loaded APC denied when ray shape-query margin is unavailable");
        require(level.getChunkSource().getChunkNow(absentX, absentZ) == null,
                "probe did not load absent margin chunk");
        body.discard();
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
