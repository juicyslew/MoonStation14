package com.juicyslew.moonstation14.gametest.hands;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyBlockUseTargetProbeGameTests {
    private BodyBlockUseTargetProbeGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void admitsExactRayFaceAndDeniesWrongFaceObstructionAndRange(GameTestHelper helper) {
        BlockPos bodyPos = new BlockPos(2, 1, 2);
        Villager body = helper.spawn(EntityType.VILLAGER, bodyPos);
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setYRot(0);
        body.setXRot(0);
        helper.runAfterDelay(1, () -> {
            body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
            body.setDeltaMovement(Vec3.ZERO);
            body.setYRot(0);
            body.setXRot(0);
            Vec3 eye = body.getEyePosition();
            Vec3 look = body.getLookAngle();
            BlockPos target = BlockPos.containing(eye.add(look.scale(3.0)));
            helper.getLevel().setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            var result = BodyBlockUseTargetProbe.probe(helper.getLevel(), body, target, Direction.NORTH);
            require(result != null && result.pos().equals(target) && result.face() == Direction.NORTH,
                    "ray admits exact block and face; eye=" + eye + ", look=" + look
                            + ", currentBlock=" + body.blockPosition() + ", target=" + target + ", result=" + result);
            require(BodyBlockUseTargetProbe.probe(helper.getLevel(), body, target, Direction.SOUTH) == null, "wrong face denied");
            helper.getLevel().setBlock(target.north(), Blocks.STONE.defaultBlockState(), 3);
            require(BodyBlockUseTargetProbe.probe(helper.getLevel(), body, target, Direction.NORTH) == null, "obstruction denied");
            helper.getLevel().setBlock(target.north(), Blocks.AIR.defaultBlockState(), 3);
            BlockPos far = body.blockPosition().above(1).south(8);
            helper.getLevel().setBlock(far, Blocks.STONE.defaultBlockState(), 3);
            require(BodyBlockUseTargetProbe.probe(helper.getLevel(), body, far, Direction.NORTH) == null, "out of range denied");
            require(helper.getLevel().getBlockState(target).is(Blocks.STONE)
                            && helper.getLevel().getBlockState(far).is(Blocks.STONE),
                    "probe did not mutate blocks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rayTargetsFullOutlineOfThinExposedPipeBeyondItsArm(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos pipePos = bodyPos.above().south(3);
        var pipe = ModBlocks.GAS_PIPE_VISUAL.get();
        var originalHost = level.getBlockState(pipePos.below());
        level.setBlock(pipePos, pipe.defaultBlockState(), 3);

        // Spawn through the helper, then freeze AI and explicitly set a stable server-side pose.
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(bodyPos.getX() + .5, bodyPos.getY() + .18, bodyPos.getZ() + .5);
        body.setYRot(0);
        body.setXRot(0);

        helper.runAfterDelay(1, () -> {
            body.setPos(bodyPos.getX() + .5, bodyPos.getY() + .18, bodyPos.getZ() + .5);
            body.setDeltaMovement(Vec3.ZERO);
            body.setYRot(0);
            body.setXRot(0);
            var state = level.getBlockState(pipePos);
            var outline = pipe.getShape(state, level, pipePos, CollisionContext.empty());
            var collision = pipe.getCollisionShape(state, level, pipePos, CollisionContext.empty());
            require(outline.toAabbs().size() == 1 && outline.toAabbs().get(0).getXsize() == 1
                            && outline.toAabbs().get(0).getYsize() == 1 && outline.toAabbs().get(0).getZsize() == 1,
                    "exposed pipe has full-voxel outline selection");
            Vec3 eye = body.getEyePosition();
            require(eye.y - pipePos.getY() > .7 && eye.y - pipePos.getY() < .9,
                    "eye ray crosses pipe voxel outside the old 2-4px arm height: " + eye);
            double localY = eye.y - pipePos.getY();
            require(collision.toAabbs().stream().noneMatch(box -> .5 >= box.minX && .5 <= box.maxX
                            && localY >= box.minY && localY <= box.maxY && .5 >= box.minZ && .5 <= box.maxZ),
                    "pipe collision remains thin and misses the eye ray location");
            var hit = BodyBlockUseTargetProbe.probe(level, body, pipePos, Direction.NORTH);
            require(hit != null && hit.pos().equals(pipePos) && hit.face() == Direction.NORTH,
                    "real body eye ray selects pipe outline beyond collision geometry; hit=" + hit);
            require(BodyBlockUseTargetProbe.probe(level, body, pipePos, Direction.SOUTH) == null,
                    "wrong pipe face is denied");

            BlockPos obstruction = pipePos.north();
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            require(BodyBlockUseTargetProbe.probe(level, body, pipePos, Direction.NORTH) == null,
                    "solid obstruction blocks the pipe ray");
            require(level.getBlockState(pipePos).is(pipe) && level.getBlockState(pipePos.below()).equals(originalHost),
                    "probe preserves exposed pipe and host block");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
