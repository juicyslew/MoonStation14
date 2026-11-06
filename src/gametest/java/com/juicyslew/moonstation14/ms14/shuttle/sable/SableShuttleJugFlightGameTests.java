package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

/** Real throw from a controlled HUMAN on a translating/yawing cabin; free flight is checked in world coordinates. */
@PrefixGameTestTemplate(false)
public final class SableShuttleJugFlightGameTests {
    private SableShuttleJugFlightGameTests() { }

    @GameTestTags({"shuttle-jug-flight", "shuttle", "loose-objects"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 360)
    public static void controlledJugThrowHasInertialUnsupportedWorldFlight(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(2100, 300, 1700),
                "jug-flight-oracle", 2.5, 1, 9.5, (fixture, body) -> {
            var level = fixture.level();
            var plot = fixture.plot();
            var hands = LiveHands.initialize(body).orElseThrow(() ->
                    new GameTestAssertException("controlled human lacks resolved live hands"));
            // Keep the canonical outside shell/floor intact but open one jug-width lane through
            // the internal room partition and low ceiling, along local -Z from the machinery bay.
            // This is a bounded test scene adaptation, not a production hull or support policy.
            var cabinOrigin = fixture.cabin().origin();
            for (int z = 1; z <= 6; z++)
                plot.setStorageBlock(cabinOrigin.offset(2, 3, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            for (int y = 1; y <= 3; y++)
                plot.setStorageBlock(cabinOrigin.offset(2, y, 7), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
            var pickup = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,
                    body.position().add(0, .35, 0), stack);
            if (!level.addFreshEntity(pickup)) throw new GameTestAssertException("fixture cargo spawn failed");
            var pickupRequest = new BodyHandActionRequest(BodyHandActionRequest.Action.PICKUP, "left",
                    fixture.owner().epoch(), 1, hands.revision(), pickup.getUUID(), null);
            var pickupResult = BodyHandRequestService.apply(fixture.owner().actor(), pickupRequest);
            if (!pickupResult.accepted() || pickupResult.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)
                throw new GameTestAssertException("public nominated cargo pickup failed: " + pickupResult);
            hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            String token = hands.token("left").orElseThrow(() -> new GameTestAssertException("pickup did not own jug token"));

            ShuttleFixtureMotionService.registerFixture(plot.subLevel());
            var anchorStorage = new Vector3d(fixture.cabin().origin().getX() + 4.5,
                    fixture.cabin().origin().getY() + 1.5, fixture.cabin().origin().getZ() + 9.5);
            var pose = plot.subLevel().logicalPose();
            Vector3d initialAnchor = pose.transformPosition(new Vector3d(anchorStorage));
            var initialOrientation = new org.joml.Quaterniond(pose.orientation());
            var source = fixture.owner().actor().createCommandSourceStack().withPermission(2);
            var dispatcher = level.getServer().getCommands().getDispatcher();
            String frame = plot.engineId().toString();
            if (execute(dispatcher, "ms14 shuttle_fixture motion " + frame + " 0 .75 0 0 80", source) != 1)
                throw new GameTestAssertException("authorized native motion service rejected lift phase");
            helper.runAfterDelay(60, () -> {
                var liftedPose = plot.subLevel().logicalPose();
                Vector3d liftedAnchor = liftedPose.transformPosition(new Vector3d(anchorStorage));
            if (liftedAnchor.y < initialAnchor.y + .7)
                throw new GameTestAssertException("native controller did not lift cabin into airborne phase: before="
                        + initialAnchor + " after=" + liftedAnchor);
                requireActorFrame(fixture, body, "after native lift");
                if (execute(dispatcher, "ms14 shuttle_fixture motion " + frame + " 1 0 0 .05 120", source) != 1)
                    throw new GameTestAssertException("authorized native motion service rejected sustained translation/yaw phase");
                awaitActualFrameMotion(helper, fixture, body, token, anchorStorage, liftedAnchor,
                        new org.joml.Quaterniond(liftedPose.orientation()), source, dispatcher, frame, 0);
            });
        });
    }

    private static void awaitActualFrameMotion(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.LivingEntity body, String token, Vector3d anchorStorage,
            Vector3d motionStartAnchor, org.joml.Quaterniond initialOrientation,
            net.minecraft.commands.CommandSourceStack source,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String frame, int waited) {
        var pose = fixture.plot().subLevel().logicalPose();
        Vector3d currentAnchor = pose.transformPosition(new Vector3d(anchorStorage));
        var currentOrientation = new org.joml.Quaterniond(pose.orientation());
        var delta = new org.joml.Quaterniond(initialOrientation).invert().mul(currentOrientation).normalize();
        double yaw = Math.atan2(2 * (delta.w * delta.y + delta.x * delta.z),
                1 - 2 * (delta.y * delta.y + delta.x * delta.x));
        double translation = currentAnchor.distance(motionStartAnchor);
        requireActorFrame(fixture, body, "during sustained translation/yaw phase");
        var controller = ShuttleFixtureMotionService.snapshot(fixture.level(), fixture.plot().subLevel());
        if (translation >= .35 && Math.abs(yaw) >= .08 && controller != null && controller.active()
                && controller.worldVelocity().x() > .2 && controller.worldAngularVelocity().y() > .02) {
            // Aim at a point transformed from the same frame's storage lane. This produces a
            // long local -Z path even as the cabin has rotated in world space.
            // The canonical public action performs normal
            // loaded aim/revalidation and resolves the inherited jug profile.
            body.setXRot(0);
            Vector3d storageAim = new Vector3d(fixture.cabin().origin().getX() + 2.5,
                    fixture.cabin().origin().getY() + 2.5, fixture.cabin().origin().getZ() + 1.5);
            Vector3d worldAim = fixture.plot().subLevel().logicalPose().transformPosition(storageAim);
            Vec3 aim = new Vec3(worldAim.x, worldAim.y, worldAim.z);
            var before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            var result = BodyHandRequestService.apply(fixture.owner().actor(), BodyHandActionRequest.throwItem(
                    "left", fixture.owner().epoch(), 2, before.revision(), token, CapturedHandTarget.world(aim)));
            if (!result.accepted() || result.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                    || result.hostTransition() == null || result.hostTransition().worldEntity() == null)
                throw new GameTestAssertException("real controlled-human throw failed after measured carrier motion="
                        + translation + " yaw=" + yaw + ": " + result);
            var entity = fixture.level().getEntity(result.hostTransition().worldEntity());
            if (!(entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity jug))
                throw new GameTestAssertException("throw did not materialize canonical loose jug: " + entity);
            Vec3 launch = jug.position();
            Vec3 launchVelocity = jug.getDeltaMovement();
            if (!Double.isFinite(launchVelocity.lengthSqr()) || launchVelocity.lengthSqr() < .01)
                throw new GameTestAssertException("throw produced invalid launch velocity: " + launchVelocity);
            FlightOracle oracle = new FlightOracle(launch, launchVelocity);
            Clearance launchClearance = assertFlightLaneClearance(fixture, jug, launch, "launch point");
            double fourIntervalTravelBound = oracle.travelBound(4);
            if (fourIntervalTravelBound >= launchClearance.frontWallMarginBlocks())
                throw new GameTestAssertException("four-step inertial travel bound does not fit clear local -Z corridor: "
                        + "travelBound=" + fourIntervalTravelBound + " frontWallMargin="
                        + launchClearance.frontWallMarginBlocks() + " launchClearance=" + launchClearance);
            requireActorFrame(fixture, body, "at real throw launch");
            // Callback is scheduled one server tick later: the newly spawned entity receives one
            // ordinary entity tick before this first observation. Check that first interval, too.
            helper.runAfterDelay(1, () -> sample(helper, fixture, body, jug, launch, launchVelocity,
                    oracle, anchorStorage, initialOrientation, frame, source, dispatcher, 1));
            return;
        }
        if (waited >= 70) throw new GameTestAssertException("authorized native controller failed to sustain measured translation+yaw: "
                + "anchorStorage=" + anchorStorage + " motionStartAnchor=" + motionStartAnchor + " currentAnchor=" + currentAnchor
                + " translation=" + translation + " initialOrientation=" + initialOrientation + " currentOrientation=" + currentOrientation
                + " yaw=" + yaw + " controller=" + controller);
        helper.runAfterDelay(1, () -> awaitActualFrameMotion(helper, fixture, body, token,
                anchorStorage, motionStartAnchor, initialOrientation, source, dispatcher, frame, waited + 1));
    }

    private static int execute(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            String command, net.minecraft.commands.CommandSourceStack source) {
        try { return dispatcher.execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new GameTestAssertException("fixture motion command failed: " + failure.getMessage());
        }
    }

    private static void sample(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.LivingEntity body, com.juicyslew.moonstation14.entities.LooseItemEntity jug,
            Vec3 launch, Vec3 launchVelocity, FlightOracle oracle, Vector3d initialOrigin,
            org.joml.Quaterniond initialOrientation, String frame,
            net.minecraft.commands.CommandSourceStack source,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, int elapsed) {
        var level = fixture.level();
        requireActorFrame(fixture, body, "during unsupported jug flight");
        var movement = (dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension) (Object) jug;
        var info = movement.sable$getCollisionInfo();
        var capture = jug.nativeMoveCapture();
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(level, jug);
        String context = "tick=" + elapsed + " frame=" + (point == null ? null : point.frameId())
                + " movingFrame=" + frame
                + " support=" + (info == null ? "unknown" : info.verticalCollisionBelow)
                + " onGround=" + jug.onGround() + " pos=" + jug.position() + " vel=" + jug.getDeltaMovement()
                + " native=" + info + " moveCapture=" + capture + " launch=" + launch + " launchVelocity=" + launchVelocity
                + " body=" + body.position() + " frameOriginBefore=" + initialOrigin
                + " frameOrientationBefore=" + initialOrientation;
        var controller = ShuttleFixtureMotionService.snapshot(fixture.level(), fixture.plot().subLevel());
        if (controller == null || !controller.active() || controller.remainingTicks() <= 0
                || controller.worldVelocity().x() <= .2 || controller.worldAngularVelocity().y() <= .02)
            throw new GameTestAssertException("frame translation/yaw was not continuously measured during flight: "
                    + controller + " context=" + context);
        if (point == null || point.frameId() != null || jug.onGround()
                || info != null && info.verticalCollisionBelow)
            throw new GameTestAssertException("flight interval was not unsupported ordinary-world motion: " + context);
        // Every scheduled observation corresponds to one entity tick after release; the very
        // first world position is checked against launch point + launch velocity + gravity.
        Vec3 predicted = oracle.advance();
        Clearance clearance = assertFlightLaneClearance(fixture, jug, predicted, context);
        if (jug.position().distanceTo(predicted) > .035)
            throw new GameTestAssertException("unsupported jug deviated from independent inertial world oracle: expected="
                    + predicted + " actual=" + jug.position() + " corridorClearance=" + clearance + " context=" + context);
        if (elapsed >= 4) {
            if (execute(dispatcher, "ms14 shuttle_fixture motion_stop " + frame, source) != 1)
                throw new GameTestAssertException("could not stop bounded native flight diagnostic motion");
            helper.succeed(); return;
        }
        helper.runAfterDelay(1, () -> sample(helper, fixture, body, jug, launch, launchVelocity,
                oracle, initialOrigin, initialOrientation, frame, source, dispatcher, elapsed + 1));
    }

    private static void requireActorFrame(MovingCabinAuthorityFixture.Ready fixture,
            net.minecraft.world.entity.LivingEntity body, String phase) {
        var point = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.currentActorPoint(fixture.level(), body);
        if (point == null || !fixture.plot().engineId().equals(point.frameId()))
            throw new GameTestAssertException("controlled HUMAN lost exact cabin ownership " + phase + ": " + point);
    }

    private record Clearance(double minimumMarginBlocks, double frontWallMarginBlocks, Vector3d projectedLocalFeet) { }

    /** Check the oracle point against the actually moving frame's carved lane, bounds include item radius/height. */
    private static Clearance assertFlightLaneClearance(MovingCabinAuthorityFixture.Ready fixture,
            com.juicyslew.moonstation14.entities.LooseItemEntity jug, Vec3 worldPosition, String context) {
        var pose = fixture.plot().subLevel().logicalPose();
        // Sable's pose includes its storage-origin/pivot mapping; inverse the complete baked
        // transform instead of subtracting pose.position and rotating around an assumed origin.
        Vector3d local = pose.transformPositionInverse(new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z));
        var cabin = fixture.cabin().origin();
        double radius = jug.getBbWidth() * .5;
        double height = jug.getBbHeight();
        double xMin = cabin.getX() + 2, xMax = cabin.getX() + 3;
        double zMin = cabin.getZ() + 1, zMax = cabin.getZ() + 12;
        double floor = cabin.getY() + 1, roof = cabin.getY() + 6;
        double frontMargin = local.z - radius - zMin;
        double[] margins = {local.x - radius - xMin, xMax - (local.x + radius),
                frontMargin, zMax - (local.z + radius), local.y - floor,
                roof - (local.y + height)};
        double minimum = java.util.Arrays.stream(margins).min().orElse(Double.NEGATIVE_INFINITY);
        if (!Double.isFinite(minimum) || minimum <= 0)
            throw new GameTestAssertException("four-step oracle path lacks source geometry clearance: local=" + local
                    + " radius=" + radius + " height=" + height + " margins=" + java.util.Arrays.toString(margins)
                    + " context=" + context);
        return new Clearance(minimum, frontMargin, local);
    }

    /** Existing LooseItemEntity free-flight law: gravity -0.04 before move; drag .995 after. */
    private static final class FlightOracle {
        private Vec3 position;
        private Vec3 velocity;
        private FlightOracle(Vec3 position, Vec3 velocity) { this.position = position; this.velocity = velocity; }
        private Vec3 advance() {
            Vec3 travel = velocity.add(0, -.04, 0);
            position = position.add(travel);
            velocity = new Vec3(travel.x * .995, travel.y * .995, travel.z * .995);
            return position;
        }
        private double travelBound(int intervals) {
            Vec3 velocity = this.velocity;
            double bound = 0;
            for (int i = 0; i < intervals; i++) {
                Vec3 travel = velocity.add(0, -.04, 0);
                bound += travel.length();
                velocity = new Vec3(travel.x * .995, travel.y * .995, travel.z * .995);
            }
            return bound;
        }
    }
}
