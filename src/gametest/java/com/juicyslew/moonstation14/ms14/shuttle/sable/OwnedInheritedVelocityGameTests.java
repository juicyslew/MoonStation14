package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real native collision regression: isolate Sable's extra living inherited-velocity input. */
@PrefixGameTestTemplate(false)
@GameTestTags({"shuttle", "shuttle-owned-airborne-impact-regression"})
public final class OwnedInheritedVelocityGameTests {
    private OwnedInheritedVelocityGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=650)
    public static void ownedTrackedCharacterHasSingleMotionAuthority(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(7000, 300, 6000), "inherited-velocity",
                2.5, 1, 2.5, (fixture, body) -> {
            fixture.setIntentPumpEnabled(false);
            // One initial placement into leased, loaded ordinary-world air outside the hull.
            var frame = fixture.plot().subLevel();
            var local = new org.joml.Vector3d(fixture.cabin().origin().getX() + 10.0,
                    fixture.cabin().origin().getY() + 4.0, fixture.cabin().origin().getZ() + 0.0);
            var world = frame.logicalPose().transformPosition(local);
            body.moveTo(world.x, world.y, world.z, body.getYRot(), body.getXRot());
            body.setOnGround(false);
            LivingEntityMovementExtension living = (LivingEntityMovementExtension) (Object) body;
            EntityMovementExtension movement = (EntityMovementExtension) (Object) body;
            // moveTo changes position, not Sable's published tracking identity. First perform a real,
            // unseeded native move so SDK collision can naturally detach this actor from the cabin.
            var beforeDepartureInfo = movement.sable$getCollisionInfo();
            Vec3 departureBefore = body.position();
            body.move(MoverType.SELF, new Vec3(0, -.001, 0));
            var departureInfo = movement.sable$getCollisionInfo();
            var actorPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            String departureContext = "beforeInfo=" + beforeDepartureInfo + " departureInfo=" + departureInfo
                    + " tracking=" + movement.sable$getTrackingSubLevel() + " point=" + actorPoint
                    + " before=" + departureBefore + " after=" + body.position();
            require(departureInfo != null && departureInfo != beforeDepartureInfo
                            && departureInfo.trackingSubLevel == null
                            && movement.sable$getTrackingSubLevel() == null
                            && actorPoint != null && actorPoint.frameId() == null,
                    "actual native departure move must establish fresh ordinary-world identity before seeding; "
                            + departureContext);

            Vec3 seed = new Vec3(.2, .05, -.1);
            living.sable$getInheritedVelocity().set(seed.x, seed.y, seed.z);
            Vec3 beforeRaw = body.position();
            Vec3 rawRequest = new Vec3(0, -.001, 0);
            SubLevelEntityCollision.CollisionInfo beforeInfo = movement.sable$getCollisionInfo();
            body.move(MoverType.SELF, rawRequest);
            Vec3 afterRaw = body.position();
            Vec3 rawDelta = afterRaw.subtract(beforeRaw);
            SubLevelEntityCollision.CollisionInfo rawInfo = movement.sable$getCollisionInfo();
            String rawContext = "request=" + rawRequest + " delta=" + rawDelta + " info=" + rawInfo
                    + " seeded=" + living.sable$getInheritedVelocity() + " before=" + beforeRaw + " after=" + afterRaw;
            require(rawInfo != null && rawInfo != beforeInfo && rawInfo.inheritedMotion == null
                            && rawInfo.trackingSubLevel == null && movement.sable$getTrackingSubLevel() == null,
                    "raw stock move must produce fresh untracked collision info; " + rawContext);
            require(rawDelta.distanceTo(new Vec3(.2, .049, -.1)) <= 1.0e-6,
                    "unscoped control must retain stock SDK inherited velocity; " + rawContext);
            require(living.sable$getInheritedVelocity().distance(new org.joml.Vector3d(seed.x, seed.y, seed.z)) <= 1.0e-9,
                    "stock native move unexpectedly changed seeded field before owned oracle; " + rawContext);

            // No teleport/reposition between control and oracle. Same body, adjacent clear-air move.
            Vec3 beforeOwned = body.position();
            Vec3 ownedRequest = new Vec3(0, -.001, 0);
            var owned = SableOwnedBodyMotion.move(body, new MovementVector(0, -.001, 0),
                    new SableOwnedBodyMotion.MoveOptions(false, null,
                            SableOwnedBodyMotion.CollisionPosePolicy.STOCK_NATIVE_INTERVAL,
                            SableOwnedBodyMotion.BodyPosePolicy.TRACKED_CHARACTER, false, false));
            Vec3 afterOwned = body.position();
            Vec3 ownedDelta = afterOwned.subtract(beforeOwned);
            var scope = owned.collisionScope();
            var nativeInfo = owned.collisionInfo();
            String context = "raw{" + rawContext + "} ownedRequest=" + ownedRequest + " ownedDelta=" + ownedDelta
                    + " fresh=" + owned.collisionInfoFresh() + " info=" + nativeInfo + " scope=" + scope
                    + " seeded=" + living.sable$getInheritedVelocity() + " before=" + beforeOwned + " after=" + afterOwned;
            require(owned.collisionInfoFresh() && nativeInfo != null && nativeInfo != rawInfo
                            && nativeInfo.inheritedMotion == null && nativeInfo.trackingSubLevel == null
                            && movement.sable$getTrackingSubLevel() == null,
                    "owned oracle requires fresh actual SDK collision info without frame carry; " + context);
            require(ownedDelta.distanceTo(ownedRequest) <= 1.0e-6,
                    "tracked owned native move must consume own request only, not SDK inherited velocity; " + context);
            require(scope != null && scope.nativeCollisionTraceComplete() && scope.nativeCollisionTrace().isEmpty()
                            && scope.worldClipObservation() != null && scope.worldClipObservation().complete(),
                    "owned oracle must prove actual scoped native call and identity world clipping without SAT contacts; " + context);
            require(living.sable$getInheritedVelocity().distance(new org.joml.Vector3d(seed.x, seed.y, seed.z)) <= 1.0e-9,
                    "suppression must not clear or mutate SDK inherited-velocity state; " + context);
            var finalPoint = LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
            require(finalPoint != null && finalPoint.frameId() == null,
                    "body unexpectedly acquired frame tracking during clear-air oracle: point=" + finalPoint + "; " + context);
            helper.succeed();
        });
    }

    private static GameTestAssertException fail(String message) { return new GameTestAssertException(message); }
    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
