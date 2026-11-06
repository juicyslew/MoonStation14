package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFinalFootClearance.Result;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Blocks;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live consumer oracle for known world/frame foot-band clearance. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle-final-foot-clearance", "shuttle"})
public final class NativeFinalFootClearanceGameTests {
    private NativeFinalFootClearanceGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void worldAndNativeFrameFloorClassification(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2400, 300, 1660),
                "foot-clearance", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            var cabin = fixture.cabin();
            var yaw = captureNativeYaw(body);
            var nativeInfo = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                    (Object) body).sable$getCollisionInfo();
            var position = body.position();
            var velocity = body.getDeltaMovement();
            var bodyBounds = body.getBoundingBox();
            // The actor is placed on the authored cabin deck by the shared fixture. Remove that
            // real native-frame floor before asking for the empty-band control.
            var floor = cabin.origin().offset(2, 0, 2);
            require(fixture.plot().setStorageBlock(floor, Blocks.AIR.defaultBlockState()),
                    "remove fixture-authored native floor at " + floor);
            var clear = NativeFinalFootClearance.classify(level, body, yaw, 16, 1);
            require(clear == Result.CLEAR,
                    "empty world and native-frame bands classify clear; result=" + clear
                            + " body=" + body.position() + " floor=" + floor + " frame=" + fixture.plot().engineId());
            var candidate = body.getBoundingBox();
            require(NativeFinalFootClearance.classifyCandidateBox(level, body, candidate, yaw) == Result.CLEAR,
                    "full candidate body volume is clear after floor removal");
            var upperBody = floor.above(1);
            require(fixture.plot().setStorageBlock(upperBody, Blocks.STONE.defaultBlockState()),
                    "native upper-body obstruction authored at " + upperBody);
            require(NativeFinalFootClearance.classify(level, body, yaw, 16, 1) == Result.CLEAR,
                    "upper-body block does not intersect the foot band");
            require(NativeFinalFootClearance.classifyCandidateBox(level, body, candidate, yaw) == Result.OBSTRUCTED,
                    "full candidate body volume detects native upper-body obstruction");
            require(fixture.plot().setStorageBlock(upperBody, Blocks.AIR.defaultBlockState()),
                    "remove native upper-body obstruction");
            var far = candidate.move(1_000_000, 0, 1_000_000);
            require(NativeFinalFootClearance.classifyCandidateBox(level, body, far, yaw) == Result.UNKNOWN,
                    "candidate query at unloaded world location is unknown");
            require(fixture.plot().setStorageBlock(floor, Blocks.STONE.defaultBlockState()),
                    "native floor authored at " + floor);
            var blocked = NativeFinalFootClearance.classify(level, body, yaw, 16, 1);
            require(blocked == Result.OBSTRUCTED,
                    "native cabin floor intersects support band; result=" + blocked + " body=" + body.position()
                            + " floor=" + floor + " frame=" + fixture.plot().engineId());
            require(body.position().equals(position) && body.getDeltaMovement().equals(velocity)
                            && body.getBoundingBox().equals(bodyBounds)
                            && ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                            (Object) body).sable$getCollisionInfo() == nativeInfo,
                    "classification is read-only for body pose, velocity, and native collision info");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    @GameTestTags({"shuttle-final-foot-clearance", "shuttle-world-candidate-clearance"})
    public static void ordinaryWorldFloorCollisionAndOverheadClearance(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2800, 300, 1660),
                "foot-world", 2.5, 1, 2.5, (fixture, body) -> {
            var level = fixture.level();
            // Put the authored world geometry at the body’s current world footprint, away from
            // the fixture support pad; keep the actor/frame pose fixed throughout the queries.
            var p = new BlockPos((int)Math.floor(body.getX()), 300, (int)Math.floor(body.getZ()));
            var nativeFloor = fixture.cabin().origin().offset(2, 0, 2);
            var yaw = captureNativeYaw(body);
            // Capture/setup movement is complete: establish the exact readonly baseline now.
            var pose = body.position();
            var grounded = body.onGround();
            var info = ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                    (Object) body).sable$getCollisionInfo();
            require(Math.abs(body.getBoundingBox().minY - 301d) < .002,
                    "world-foot fixture must start at deck height near 301; feet=" + body.getBoundingBox().minY);
            require(fixture.plot().setStorageBlock(nativeFloor, Blocks.AIR.defaultBlockState()),
                    "remove fixture-authored native floor at " + nativeFloor);
            for (int x = p.getX()-1; x <= p.getX()+1; x++)
                for (int y = p.getY()-2; y <= p.getY()+1; y++)
                    for (int z = p.getZ()-1; z <= p.getZ()+1; z++)
                        writeWorldAir(helper, new BlockPos(x,y,z));
            require(new AABB(p.getX(), p.getY(), p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1)
                            .intersects(NativeFinalFootClearance.footBand(body)),
                    "authored world floor cell must intersect queried foot band: " + p);
            var clear = NativeFinalFootClearance.classify(level, body, yaw, 16, 1);
            require(clear == Result.CLEAR,
                    "ordinary world band without a floor is clear; result=" + clear + " body=" + body.position()
                            + " nativeFloor=" + nativeFloor + " worldFoot=" + p);
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            var blocked = NativeFinalFootClearance.classify(level, body, yaw, 16, 1);
            require(blocked == Result.OBSTRUCTED,
                    "ordinary full-cube world floor intersects foot band; result=" + blocked + " body=" + body.position()
                            + " worldFloor=" + p);
            require(body.position().equals(pose) && body.onGround() == grounded
                            && ((dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension)
                            (Object) body).sable$getCollisionInfo() == info,
                    "world classification leaves pose, grounded flag, and collision info unchanged");
            // A block separated from the foot band is not a false positive.
            level.setBlock(p.above(2), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            var overheadClear = NativeFinalFootClearance.classify(level, body, yaw, 16, 1);
            require(overheadClear == Result.CLEAR,
                    "overhead geometry separated from empty foot band is clear; result=" + overheadClear
                            + " body=" + body.position() + " overhead=" + p.above(2));
            var upperBody = p.above(2);
            var upperBounds = new AABB(upperBody.getX(), upperBody.getY(), upperBody.getZ(),
                    upperBody.getX()+1, upperBody.getY()+1, upperBody.getZ()+1);
            level.setBlock(upperBody, Blocks.STONE.defaultBlockState(), 3);
            require(!upperBounds.intersects(NativeFinalFootClearance.footBand(body)),
                    "upper-body obstruction is geometrically disjoint from foot band");
            require(upperBounds.intersects(body.getBoundingBox()),
                    "same upper-body obstruction intersects full body bounds");
            require(NativeFinalFootClearance.classify(level, body, yaw, 16, 1) == Result.CLEAR,
                    "upper-body obstruction remains separated from foot band");
            require(NativeFinalFootClearance.classifyCandidateBox(level, body, body.getBoundingBox(), yaw)
                            == Result.OBSTRUCTED,
                    "full candidate body query detects ordinary-world overhead block");
            level.setBlock(upperBody, Blocks.AIR.defaultBlockState(), 3);
            require(NativeFinalFootClearance.classifyCandidateBox(level, body, body.getBoundingBox(), yaw) == Result.CLEAR,
                    "full candidate body query clears after overhead removal");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    @GameTestTags({"shuttle-final-foot-clearance"})
    public static void missingWorldChunkIsUnknownWithoutLoading(GameTestHelper helper) {
        var level = helper.getLevel();
        int x = 1_000_000;
        int z = 1_000_000;
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        require(chunk == null, "selected far-world chunk starts absent");
        Marker body = new Marker(EntityType.MARKER, level);
        body.setPos(x + .5, 300, z + .5);
        body.setBoundingBox(new AABB(x + .2, 300, z + .2, x + .8, 301.8, z + .8));
        var position = body.position();
        var bounds = body.getBoundingBox();
        var onGround = body.onGround();
        require(NativeFinalFootClearance.classify(level, body, 16, 1) == Result.UNKNOWN,
                "absent ordinary-world chunk makes classification unknown");
        require(level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null,
                "classification does not load the absent world chunk");
        require(body.position().equals(position) && body.getBoundingBox().equals(bounds)
                        && body.onGround() == onGround,
                "unknown classification leaves the unspawned query body unchanged");
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static NativeActorYaw captureNativeYaw(net.minecraft.world.entity.Entity body) {
        var mob = (net.minecraft.world.entity.Mob) body;
        var ordinary = SableOwnedBodyMotion.move(mob, new MovementVector(0, 0, 0),
                new SableOwnedBodyMotion.MoveOptions(false, null,
                        SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                        SableOwnedBodyMotion.BodyPosePolicy.STOCK_SDK));
        require(ordinary.collisionScope() == null, "ordinary STOCK_SDK movement has no owned proof scope");
        var moved = SableOwnedBodyMotion.move(mob, new MovementVector(0, -.001, 0),
                new SableOwnedBodyMotion.MoveOptions(false, LoadedFrameRayQuery.currentActorPoint(body.level(), body),
                        SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                        SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER));
        require(moved.collisionScope() != null, "tracked character movement supplies owned proof scope");
        return NativeActorYaw.capture(body, moved.collisionScope()).orElseThrow(
                () -> new net.minecraft.gametest.framework.GameTestAssertException("native yaw capture unavailable: " + moved));
    }
    private static void writeWorldAir(GameTestHelper helper, BlockPos pos) {
        var before = helper.getLevel().getBlockState(pos);
        boolean written = helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        require(helper.getLevel().getBlockState(pos).isAir() && (written || before.isAir()), "world AIR write " + pos);
    }
}
