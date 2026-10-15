package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
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
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "interaction"})
public final class BodyItemTargetProbeGameTests {
    private BodyItemTargetProbeGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void bodyEyeFirstItemAndBlocker(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, origin);
        ItemEntity near = item(level, origin, 2);
        require(BodyItemTargetProbe.probe(level, body, near, 4) != null, "body eye sees item");
        require(BodyItemTargetProbe.probeSelected(level, body, near.getUUID(), 4) != null,
                "explicit selection accepts a visible pile member");
        require(BodyItemTargetProbe.probe(level, body, near, 5.01) == null, "reach ceiling");
        require(BodyItemTargetProbe.probe(level, body, near, Double.NaN) == null, "nonfinite reach");
        ItemEntity far = item(level, origin, 3);
        require(BodyItemTargetProbe.probe(level, body, far, 4) == null, "nearer item wins");
        require(BodyItemTargetProbe.probeSelected(level, body, far.getUUID(), 4) != null,
                "explicit selection can select a farther visible pile member");
        require(BodyItemTargetProbe.probe(level, body, near, 4) != null, "far item does not hide near one");
        body.setYRot(90);
        body.setYHeadRot(90);
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "wrong body look");
        require(BodyItemTargetProbe.visibleCandidates(level, body, 4).isEmpty(),
                "unselected automatic candidates remain current-look-ray limited");
        require(BodyItemTargetProbe.probeSelected(level, body, near.getUUID(), 4) != null,
                "explicitly nominated current target does not require the server look ray");
        body.setYRot(-90);
        body.setYHeadRot(-90);
        BlockPos barrier = origin.offset(1, 1, 0);
        level.setBlock(barrier, Blocks.STONE.defaultBlockState(), 3);
        require(BodyItemTargetProbe.probe(level, body, near, 4) == null, "stone blocks ray");
        require(BodyItemTargetProbe.probeSelected(level, body, near.getUUID(), 4) == null,
                "selected target behind a current block occluder is denied");
        level.setBlock(barrier, Blocks.AIR.defaultBlockState(), 3);
        near.setPos(body.getX() + 6, near.getY(), near.getZ());
        require(BodyItemTargetProbe.probeSelected(level, body, near.getUUID(), 4) == null,
                "selected target moved beyond authoritative reach is denied");
        near.setPos(origin.getX() + 2.5, origin.getY() + 1.5, origin.getZ() + .5);
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

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    @GameTestTags({"npc-physics", "ingestion"})
    public static void nearFloorTargetRemainsVisibleWhenFullReachWouldCrossBuildFloor(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int x = -1; x <= 2; x++) for (int y = -4; y <= 2; y++)
            level.setBlock(origin.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "ordinary mouse constructed for floor-edge gaze");
        mouse.setNoAi(true);
        mouse.setPos(origin.getX() + .5, origin.getY() - 2, origin.getZ() + .5);
        require(level.addFreshEntity(mouse), "mouse added at the normal superflat lower-world floor");
        Vec3 eye = mouse.getEyePosition();
        mouse.setYRot(-90);
        mouse.setYHeadRot(-90);
        mouse.setYBodyRot(-90);
        mouse.setXRot(75);
        Vec3 look = mouse.getLookAngle();
        Vec3 aim = eye.add(look.scale(.25));
        var cheese = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                new Vec3(aim.x, aim.y - .125, aim.z), new ItemStack(com.juicyslew.moonstation14.item.ModItems.CHEESE_SLICE.get()));
        require(level.addFreshEntity(cheese), "registered cheese source lies on the close downward gaze");
        Vec3 requestedEnd = eye.add(look.scale(BodyItemTargetProbe.MAX_REACH));
        require(requestedEnd.y < level.getMinBuildHeight(),
                "fixture full-reach look ray extends below the build floor: " + requestedEnd);
        require(BodyItemTargetProbe.visibleCandidates(level, mouse, BodyItemTargetProbe.MAX_REACH).stream()
                        .anyMatch(candidate -> candidate.itemId().equals(cheese.getUUID())),
                "automatic visible-candidate ray retains the near-floor target after endpoint clipping");
        var selected = BodyItemTargetProbe.probeSelectedDiagnostic(level, mouse, cheese.getUUID(),
                BodyItemTargetProbe.MAX_REACH);
        require(selected.target() != null,
                "explicit selected UID independently remains valid through current range and LOS: "
                        + selected.diagnostic());
        cheese.discard();
        mouse.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
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

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void itemOverlappingEyeRemainsVisibleAtZeroDistanceBlockHit(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, origin);
        Vec3 eye = body.getEyePosition();
        ItemEntity overlap = new ItemEntity(level, eye.x, eye.y - .1, eye.z,
                new ItemStack(Items.DIAMOND));
        require(level.addFreshEntity(overlap), "overlapping item spawned");
        level.setBlock(BlockPos.containing(eye), Blocks.STONE.defaultBlockState(), 3);
        require(BodyItemTargetProbe.probe(level, body, overlap, 4) != null,
                "eye-overlapping item remains eligible when collider hit is at eye");
        overlap.discard();
        body.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void equalDistanceUuidWinsWhenLowSpawnsFirst(GameTestHelper helper) {
        tie(helper, true);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void equalDistanceUuidWinsWhenHighSpawnsFirst(GameTestHelper helper) {
        tie(helper, false);
    }

    private static void tie(GameTestHelper helper, boolean lowFirst) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Villager body = body(level, origin);
        UUID lowId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ItemEntity low = new ItemEntity(level, origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, new ItemStack(Items.DIAMOND));
        ItemEntity high = new ItemEntity(level, origin.getX() + 2.5, origin.getY() + 1.5,
                origin.getZ() + .5, new ItemStack(Items.EMERALD));
        low.setUUID(lowId);
        high.setUUID(highId);
        require(level.addFreshEntity(lowFirst ? low : high), "first tie item spawned");
        require(level.addFreshEntity(lowFirst ? high : low), "second tie item spawned");
        require(lowId.equals(BodyItemTargetProbe.nearestVisible(level, body, 4).itemId()),
                "lower UUID wins exact tie regardless of spawn order");
        require(BodyItemTargetProbe.probe(level, body, low, 4) != null, "tie does not deny winner");
        require(BodyItemTargetProbe.probe(level, body, high, 4) == null, "higher UUID not first");
        low.discard();
        high.discard();
        body.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
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
