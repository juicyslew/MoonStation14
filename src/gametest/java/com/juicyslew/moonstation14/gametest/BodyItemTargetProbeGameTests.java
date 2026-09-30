package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyItemTargetProbeGameTests {
    private BodyItemTargetProbeGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bodyEyeFirstItemAndBlocker(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, origin);
        ItemEntity near = item(level, origin, 2);
        require(BodyItemTargetProbe.probe(level, body, near, 4) != null, "body eye sees item");
        require(BodyItemTargetProbe.probe(level, body, near, 5.01) == null, "reach ceiling");
        require(BodyItemTargetProbe.probe(level, body, near, Double.NaN) == null, "nonfinite reach");
        ItemEntity far = item(level, origin, 3);
        require(BodyItemTargetProbe.probe(level, body, far, 4) == null, "nearer item wins");
        require(BodyItemTargetProbe.probe(level, body, near, 4) != null, "far item does not hide near one");
        body.setYRot(90);
        body.setYHeadRot(90);
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "wrong body look");
        body.setYRot(-90);
        body.setYHeadRot(-90);
        BlockPos barrier = origin.offset(1, 1, 0);
        level.setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "stone blocks ray");
        level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
        near.setTarget(UUID.randomUUID());
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "reserved for another owner");
        near.setTarget(null);
        near.discard();
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "removed target");
        body.discard();
        require(BodyItemTargetProbe.probe(level, body, far, 4) == null, "removed body");
        far.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nearMissAndInflatedWallCannotHideFirstItem(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, origin);
        ItemEntity near = item(level, origin, 2);
        near.setPos(near.getX(), near.getY(), near.getZ() + .26);
        Vec3 from = body.getEyePosition();
        Vec3 end = from.add(body.getLookAngle().scale(4));
        require(near.getBoundingBox().clip(from, end).isEmpty(), "unmodified ray misses item");
        require(BodyItemTargetProbe.probe(level, body, near, 4) != null, "small miss is eligible");
        ItemEntity far = item(level, origin, 3);
        require(BodyItemTargetProbe.probe(level, body, far, 4) == null, "nearest inflated item wins");
        near.discard();
        far.discard();
        BlockPos wall = origin.offset(2, 1, 0);
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        ItemEntity behind = item(level, origin, 2);
        behind.setPos(wall.getX() + 1.15, behind.getY(), behind.getZ());
        require(BodyItemTargetProbe.probe(level, body, behind, 4) == null,
                "inflated box before collider cannot expose item behind wall");
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
        behind.discard();
        body.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absentShapeMarginDeniesWithoutLoading(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        int rayX = 0, rayZ = 0, absentX = 0, absentZ = 0;
        boolean found = false;
        for (int dx = -8; dx <= 8 && !found; dx++) {
            for (int dz = -8; dz <= 8 && !found; dz++) {
                int x = (origin.getX() >> 4) + dx, z = (origin.getZ() >> 4) + dz;
                if (level.getChunkSource().getChunkNow(x, z) == null) continue;
                BlockPos candidate = new BlockPos((x << 4) + 10, origin.getY() + 1, (z << 4) + 8);
                if (!level.getChunkSource().getChunkNow(x, z).getBlockState(candidate).isAir()) continue;
                for (int mx = x - 1; mx <= x + 1 && !found; mx++) {
                    for (int mz = z - 1; mz <= z + 1; mz++) {
                        if (level.getChunkSource().getChunkNow(mx, mz) != null) continue;
                        rayX = x; rayZ = z; absentX = mx; absentZ = mz; found = true;
                        break;
                    }
                }
            }
        }
        require(found, "loaded ray chunk with missing margin");
        BlockPos bodyPos = new BlockPos((rayX << 4) + 8, origin.getY(), (rayZ << 4) + 8);
        Villager body = body(level, bodyPos);
        ItemEntity target = item(level, bodyPos, 2);
        require(level.getChunkSource().getChunkNow(absentX, absentZ) == null, "margin absent before probe");
        require(BodyItemTargetProbe.probe(level, body, target, 4) == null, "missing margin denied");
        require(level.getChunkSource().getChunkNow(absentX, absentZ) == null, "probe did not load margin");
        target.discard();
        body.discard();
        helper.succeed();
    }

    private static Villager body(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "villager created");
        body.setNoAi(true);
        body.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        body.setYRot(-90);
        body.setYHeadRot(-90);
        body.setXRot(0);
        require(level.addFreshEntity(body), "body spawned");
        return body;
    }

    private static ItemEntity item(net.minecraft.server.level.ServerLevel level, BlockPos origin, int offset) {
        ItemEntity item = new ItemEntity(level, origin.getX() + .5 + offset,
                origin.getY() + 1.5, origin.getZ() + .5, new ItemStack(Items.DIAMOND));
        require(level.addFreshEntity(item), "item spawned");
        return item;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
