package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-final-foot-clearance", "shuttle"})
public final class NativeOrientedFootClearanceGameTests {
    private NativeOrientedFootClearanceGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void orientedFrameCornerAndClearControl(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(3200, 300, 1860), "oriented-foot",
                2.71, .9995, 2.71, (fixture, body) -> {
            var frame = fixture.plot().subLevel();
            var rotation = new Quaterniond().rotationY(Math.PI / 4);
            frame.logicalPose().orientation().set(rotation);
            RigidBodyHandle.of(frame).teleport(frame.logicalPose().position(), rotation);
            frame.updateLastPose();
            frame.updateBoundingBox();
            SableFixtureScene.awaitStationaryFrame(helper, fixture.plot(), 80, () -> {
                var localFeet = new Vector3d(fixture.cabin().origin().getX() + 2.71,
                        fixture.cabin().origin().getY() + .9995, fixture.cabin().origin().getZ() + 2.71);
                var worldFeet = frame.logicalPose().transformPosition(localFeet);
                body.moveTo(worldFeet.x, worldFeet.y, worldFeet.z, body.getYRot(), body.getXRot());
                var moved = SableOwnedBodyMotion.move(body, new MovementVector(0, -.001, 0),
                        new SableOwnedBodyMotion.MoveOptions(false, LoadedFrameRayQuery.currentActorPoint(
                                fixture.level(), body), SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                                SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER));
                var yaw = NativeActorYaw.capture(body, moved.collisionScope()).orElseThrow(
                        () -> new GameTestAssertException("owned yaw missing: " + moved));
                require(Math.abs(Math.abs(yaw.yaw()) - Math.PI / 4) < .02,
                        "expected native actor yaw 45 degrees, got " + yaw.yaw());
                var floor = fixture.cabin().origin().offset(3, 0, 3);
                for (int x = 1; x <= 5; x++) for (int y = 0; y <= 3; y++) for (int z = 1; z <= 5; z++)
                    writeOrVerify(helper, fixture, fixture.cabin().origin().offset(x, y, z), Blocks.AIR.defaultBlockState());
                // Clear ordinary-world blocks throughout the oriented foot-band envelope and its margin.
                var band = NativeFinalFootClearance.footBand(body);
                var envelope = FrameBoxIntersection.orientedWorldBounds(band, yaw.orientation());
                require(envelope != null, "oriented world foot-band envelope available");
                for (int x = (int)Math.floor(envelope.minX)-1; x <= (int)Math.floor(envelope.maxX)+1; x++)
                    for (int y = (int)Math.floor(envelope.minY)-1; y <= (int)Math.floor(envelope.maxY)+1; y++)
                        for (int z = (int)Math.floor(envelope.minZ)-1; z <= (int)Math.floor(envelope.maxZ)+1; z++)
                            writeWorldAir(helper, new BlockPos(x, y, z));
                writeOrVerify(helper, fixture, floor, Blocks.AIR.defaultBlockState());
                require(NativeFinalFootClearance.classify(fixture.level(), body, yaw) == NativeFinalFootClearance.Result.CLEAR,
                        "empty rotated frame control must clear");
                writeOrVerify(helper, fixture, floor, Blocks.STONE.defaultBlockState());
                var pose = body.position(); var velocity = body.getDeltaMovement(); var bounds = body.getBoundingBox();
                var info = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                        (Object)body).sable$getCollisionInfo();
                var localCube = new net.minecraft.world.phys.AABB(floor.getX(), floor.getY(), floor.getZ(),
                        floor.getX() + 1, floor.getY() + 1, floor.getZ() + 1);
                require(!FrameBoxIntersection.intersects(NativeFinalFootClearance.footBand(body), localCube,
                                frame.logicalPose()), "identity footprint must miss selected corner");
                require(FrameBoxIntersection.intersects(NativeFinalFootClearance.footBand(body),
                                new Quaterniond(yaw.orientation()),
                                localCube, frame.logicalPose()), "yaw-oriented footprint must hit selected corner");
                require(NativeFinalFootClearance.classify(fixture.level(), body, yaw)
                                == NativeFinalFootClearance.Result.OBSTRUCTED, "oriented corner must obstruct");
                require(NativeFinalFootClearance.classify(fixture.level(), body)
                                == NativeFinalFootClearance.Result.UNKNOWN, "frame without yaw must be unknown");
                require(body.position().equals(pose) && body.getDeltaMovement().equals(velocity)
                                && body.getBoundingBox().equals(bounds)
                                && ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                                (Object)body).sable$getCollisionInfo() == info, "queries must be read-only");
                helper.succeed();
            });
        });
    }

    private static void writeOrVerify(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                     BlockPos pos, net.minecraft.world.level.block.state.BlockState desired) {
        var before = fixture.level().getBlockState(pos);
        boolean written = fixture.plot().setStorageBlock(pos, desired);
        require(fixture.level().getBlockState(pos).equals(desired) && (written || before.equals(desired)),
                "frame write failed at " + pos + " desired=" + desired + " before=" + before);
    }
    private static void writeWorldAir(GameTestHelper helper, BlockPos pos) {
        var before = helper.getLevel().getBlockState(pos);
        boolean written = helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(helper.getLevel().getBlockState(pos).isAir() && (written || before.isAir()), "world AIR write " + pos);
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
