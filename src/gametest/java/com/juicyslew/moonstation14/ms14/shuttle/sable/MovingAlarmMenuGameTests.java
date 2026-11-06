package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmControlSnapshot;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui.AirAlarmMenu;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;

@GameTestTags({"shuttle-menu-access", "shuttle"})
@PrefixGameTestTemplate(false)
public final class MovingAlarmMenuGameTests {
    private MovingAlarmMenuGameTests() { }

    @GameTest(template = "atmos_large_empty", templateNamespace = "moonstation14", timeoutTicks = 850, batch = "shuttle_moving_alarm_menu")
    public static void paidAlarmContinuationSurvivesNativeFrameMotionButNotReplacementOrWall(GameTestHelper helper) {
        MovingCabinAuthorityFixture.start(helper, new BlockPos(680, 300, 640), "moving-alarm-owner", 5.5, 4, 10.5, (fixture, body) -> {
            var alarmPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.MACHINE);
            var point = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos);
            if (point == null || !fixture.plot().engineId().equals(point.frameId()))
                throw new GameTestAssertException("alarm target lacks exact authored frame UUID");
            // NORTH alarm outline occupies the positive-Z face strip; aim inside that actual mapped shape.
            Vec3 contact = point.worldPoint().add(0, 0, .45);
            long sequence = fixture.owner().viewTo(body, contact);
            Vec3 direction = contact.subtract(body.getEyePosition()).normalize();
            BoundedGameTestWait.await(helper, 10,
                    () -> "registered alarm view intent not applied through sequence " + sequence,
                    () -> fixture.owner().drainAppliedSequence() >= sequence && body.getLookAngle().dot(direction) > .995,
                    () -> afterViewInput(helper, fixture, body));
        });
    }

    private static void afterViewInput(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                       net.minecraft.world.entity.LivingEntity body) {
            var actor = fixture.owner().actor();
            var authority = fixture.owner().authority();
            var alarmPos = fixture.cabin().at(com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleCabinFixture.MACHINE);
            var alarm = fixture.alarm();
            if (PowerRuntime.airAlarmMeter(fixture.level(), alarm).deliveredWatts() != 100)
                throw new GameTestAssertException("alarm menu fixture requires real delivered 100W");
            var point = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos);
            if (point == null || !fixture.plot().engineId().equals(point.frameId()))
                throw new GameTestAssertException("alarm target lacks exact authored frame UUID");
            Direction face = face(fixture.level(), body, alarmPos);
            var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            var request = new BodyBlockUseRequest(authority.epoch(), 1, hands.revision(), hands.activeHand(), null,
                    alarmPos, face, false, BodyBlockUseRequest.Intent.USE);
            fixture.owner().dispatch(request);
            var blockUseOutbounds = fixture.owner().drainBlockUseOutbounds(request.sequence());
            if (!(actor.containerMenu instanceof AirAlarmMenu menu)
                    || !menu.bodyAuthority().equals(authority) || menu.bodyAuthority().epoch() != authority.epoch()
                    || !menu.bodyAuthority().mindId().equals(authority.mindId())
                    || !menu.expectedFrameId().equals(fixture.plot().engineId())
                    || menu.bodyAuthority().body() != body || !menu.stillValid(actor))
                throw new GameTestAssertException("registered body-use did not open exact bound paid alarm menu; result="
                        + blockUseOutbounds + ", face=" + face + ", currentTarget="
                        + LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos)
                        + ", deviceLinksValid=" + alarm.deviceLinksValid()
                        + ", reach=" + BodyBlockUseTargetProbe.withinReach(fixture.level(), body, alarmPos)
                        + ", LOS=" + BodyBlockUseTargetProbe.lineOfSight(fixture.level(), body, alarmPos)
                        + ", power=" + PowerRuntime.airAlarmMeter(fixture.level(), alarm)
                        + ", container=" + actor.containerMenu.getClass().getName()
                        + ", authorityMatch=" + (actor.containerMenu instanceof AirAlarmMenu candidate
                        && candidate.bodyAuthority().equals(authority))
                        + ", frame=" + (actor.containerMenu instanceof AirAlarmMenu candidate ? candidate.expectedFrameId() : null));

            // Alarm UI continuation is aim-independent: the owner turns away after opening.
            long awaySequence = fixture.owner().viewTo(body, body.getEyePosition().add(0, 0, -8));
            BoundedGameTestWait.await(helper, 10, () -> "registered alarm turn-away intent not applied: " + awaySequence,
                    () -> fixture.owner().drainAppliedSequence() >= awaySequence,
                    () -> afterTurnAway(helper, fixture, body, menu, alarmPos, point.worldPoint(), body.position()));
    }

    private static void afterTurnAway(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                      net.minecraft.world.entity.LivingEntity body, AirAlarmMenu menu,
                                      BlockPos alarmPos, Vec3 frameBefore, Vec3 bodyBefore) {
            var actor = fixture.owner().actor();
            var alarm = fixture.alarm();
            if (!menu.stillValid(actor)) throw new GameTestAssertException("turning away revoked alarm reach unexpectedly");
            SableFixtureScene.clearStoneSupportPad(fixture.level(), fixture.scene());
            RigidBodyHandle handle = RigidBodyHandle.of(fixture.plot().subLevel());
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(2, 0, 2), new org.joml.Vector3d());
            BoundedGameTestWait.await(helper, 35, () -> "native alarm frame/body did not translate; start="
                            + frameBefore + ", current=" + LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos)
                            + ", bodyStart=" + bodyBefore + ", bodyNow=" + body.position()
                            + ", velocity=" + handle.getLinearVelocity(new org.joml.Vector3d()),
                    () -> {
                        var current = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos);
                        return current != null && current.worldPoint().subtract(frameBefore).horizontalDistance() > .1
                                && body.position().subtract(bodyBefore).horizontalDistance() > .1;
                    }, () -> {
                        var current = LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos);
                        if (current == null || !fixture.plot().engineId().equals(current.frameId())
                                || !menu.expectedFrameId().equals(current.frameId()) || !menu.stillValid(actor)
                                || !PowerRuntime.airAlarmMeter(fixture.level(), alarm).known()
                                || PowerRuntime.airAlarmMeter(fixture.level(), alarm).deliveredWatts() != 100)
                            throw new GameTestAssertException("alarm continuation lost current frame, aim-independent reach, or actual paid power");
                        var continuation = new AirAlarmControlRequest(menu.containerId, menu.session(), 1,
                                new UUID(0, 0), 0, AirAlarmControlRequest.Operation.QUERY, false, 0);
                        fixture.owner().dispatch(continuation);
                        AirAlarmControlSnapshot[] response = new AirAlarmControlSnapshot[1];
                        BoundedGameTestWait.await(helper, 10,
                                () -> "registered alarm query response missing; valid=" + menu.stillValid(actor)
                                        + ", target=" + LoadedFrameRayQuery.currentTargetPoint(fixture.level(), alarmPos),
                                () -> (response[0] = takeOutbound(fixture.owner().channel(), continuation.requestId())) != null,
                                () -> {
                                    var snapshot = response[0];
                                    if (!snapshot.accepted() || !snapshot.powered()
                                            || snapshot.containerId() != menu.containerId
                                            || !snapshot.session().equals(menu.session()))
                                        throw new GameTestAssertException("registered paid alarm query was denied: " + snapshot);
                                    afterAlarmQuery(helper, fixture, body, menu, alarmPos, frameBefore, bodyBefore);
                                });
                        return;
                    });
    }

    private static void afterAlarmQuery(GameTestHelper helper, MovingCabinAuthorityFixture.Ready fixture,
                                        net.minecraft.world.entity.LivingEntity body, AirAlarmMenu menu,
                                        BlockPos alarmPos, Vec3 frameBefore, Vec3 bodyBefore) {
                        var actor = fixture.owner().actor();
                        var alarm = fixture.alarm();

                        // A nearer frame-owned solid blocks current LOS even though the stored device/frame remains.
                        BlockPos blocker = fixture.cabin().origin().offset(5, 5, 10);
                        fixture.plot().setStorageBlock(blocker, Blocks.STONE.defaultBlockState());
                        if (menu.stillValid(actor))
                            throw new GameTestAssertException("nearer solid failed to invalidate current alarm body LOS");
                        fixture.plot().setStorageBlock(blocker, Blocks.AIR.defaultBlockState());

                        fixture.level().setBlock(alarmPos, Blocks.AIR.defaultBlockState(), 3);
                        fixture.level().setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState()
                                .setValue(AirAlarmBlock.FACING, Direction.NORTH), 3);
                        if (fixture.level().getBlockEntity(alarmPos) == alarm || menu.stillValid(actor))
                            throw new GameTestAssertException("replacement alarm inherited old BE/frame menu capability");
                        helper.succeed();
    }

    private static AirAlarmControlSnapshot takeOutbound(io.netty.channel.embedded.EmbeddedChannel channel, long requestId) {
        channel.runPendingTasks(); channel.runScheduledPendingTasks();
        AirAlarmControlSnapshot found = null;
        Object next;
        while ((next = channel.readOutbound()) != null)
            if (next instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload() instanceof AirAlarmControlSnapshot snapshot
                    && snapshot.requestId() == requestId) found = snapshot;
        return found;
    }

    private static Direction face(net.minecraft.server.level.ServerLevel level,
                                  net.minecraft.world.entity.LivingEntity body, BlockPos pos) {
        for (Direction face : Direction.values())
            if (BodyBlockUseTargetProbe.probe(level, body, pos, face) != null) return face;
        throw new GameTestAssertException("server-side body ray did not hit authored Air Alarm face; "
                + MovingFrameRayDiagnostic.describe(level, body, pos));
    }
}
