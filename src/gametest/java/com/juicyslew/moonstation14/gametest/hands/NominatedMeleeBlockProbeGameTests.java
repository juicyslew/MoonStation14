package com.juicyslew.moonstation14.gametest.hands;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"melee-block-contact", "hands"})
@PrefixGameTestTemplate(false)
public final class NominatedMeleeBlockProbeGameTests {
    private NominatedMeleeBlockProbeGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="atmos_large_empty", timeoutTicks=20)
    public static void clipsOffAxisNominatedPointAndRejectsObstructionOrStaleBlock(GameTestHelper helper) {
        var body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true); body.setNoGravity(true); body.setYRot(0); body.setXRot(0);
        helper.runAfterDelay(1, () -> {
            var level = helper.getLevel();
            body.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5))); body.setDeltaMovement(Vec3.ZERO);
            // This block is deliberately outside the current +Z look ray.
            BlockPos target = helper.absolutePos(new BlockPos(5, 3, 3));
            level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
            Vec3 point = helper.absoluteVec(new Vec3(5, 3.5, 3.5));
            var hit = BodyBlockUseTargetProbe.probeNominatedContact(level, body, target, Direction.WEST, point);
            require(hit != null && hit.pos().equals(target) && hit.face() == Direction.WEST,
                    "off-axis nominated endpoint clips its exact outline face; eye=" + body.getEyePosition()
                            + " point=" + point + " face=" + Direction.WEST + " hit=" + hit);
            for (Direction face : Direction.values()) {
                Vec3 relativeFacePoint = new Vec3(5.5, 3.5, 3.5)
                        .add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
                Vec3 facePoint = helper.absoluteVec(relativeFacePoint);
                Vec3 eyePosition = relativeFacePoint.add(face.getStepX() * .5, face.getStepY() * .5,
                        face.getStepZ() * .5);
                Vec3 feetPosition = eyePosition.subtract(0, body.getEyeHeight(), 0);
                body.setPos(helper.absoluteVec(feetPosition));
                var faceHit = BodyBlockUseTargetProbe.probeNominatedContact(level, body, target, face, facePoint);
                require(faceHit != null && faceHit.pos().equals(target) && faceHit.face() == face,
                        "outside contact clips nominated " + face + " face; eye=" + body.getEyePosition()
                                + " point=" + facePoint + " hit=" + faceHit);
            }
            body.setPos(helper.absoluteVec(new Vec3(14.5, 1, 3.5)));
            require(BodyBlockUseTargetProbe.probeNominatedContact(level, body, target, Direction.WEST, point) == null,
                    "origin beyond maximum reach denied; eye=" + body.getEyePosition() + " point=" + point);
            body.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
            BlockPos obstruction = target.west();
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
            require(BodyBlockUseTargetProbe.probeNominatedContact(level, body, target, Direction.WEST, point) == null,
                    "intervening outline denies nomination");
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            require(BodyBlockUseTargetProbe.probeNominatedContact(level, body, target, Direction.WEST, point) == null,
                    "stale/removed nominated block denied");
            helper.succeed();
        });
    }

    private static void require(boolean pass, String message) { if (!pass) throw new GameTestAssertException(message); }
}
