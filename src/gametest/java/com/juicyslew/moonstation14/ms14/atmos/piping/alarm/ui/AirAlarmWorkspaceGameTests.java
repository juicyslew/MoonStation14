package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.hands.network.AuthenticatedBlockUseDriver;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registered-player admission coverage for the nonmodal, server-owned AirAlarm workspace. */
@PrefixGameTestTemplate(false)
@GameTestTags({"air-alarm", "air-alarm-workspace"})
public final class AirAlarmWorkspaceGameTests {
    private AirAlarmWorkspaceGameTests() { }

    @GameTest(template = "power_receiver_tall_clear", timeoutTicks = 900, templateNamespace = "moonstation14", batch = "air_alarm_workspace_real_power")
    public static void registeredActivationAdmitsAndClosesWorkspace(GameTestHelper h) {
        var fixture = AirAlarmPowerFixture.place(h);
        var alarmPos = fixture.alarmPos();
        var player = RegisteredServerPlayerFixture.register(fixture.level(), "air-alarm-workspace-native");
        var lease = FixtureChunkTickets.acquire(h, new net.minecraft.world.phys.AABB(
                alarmPos.getX()-3, alarmPos.getY()-2, alarmPos.getZ()-4,
                alarmPos.getX()+4, alarmPos.getY()+4, alarmPos.getZ()+3));
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = h.getTick() + 900;
        Runnable cleanup = () -> { if (closed.compareAndSet(false, true)) {
            player.close(); try { lease.close(); } catch (Exception ignored) { }
        }};
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a, net.minecraft.gametest.framework.GameTestInfo b, net.minecraft.gametest.framework.GameTestRunner r) { cleanup.run(); }
        });
        FixtureChunkTickets.awaitEntityTicking(h, lease, 800, () -> {
            try {
                var owner = RegisteredPlayerReadyDriver.readyAndQuery(player);
                owner.body().setPos(alarmPos.getX()+.5, alarmPos.getY(), alarmPos.getZ()-1.5);
                int remaining = (int) Math.max(1, deadline - h.getTick());
                BoundedGameTestWait.await(h, remaining,
                        () -> "alarm did not publish fresh 100 W before original 900 tick deadline: "
                                + meterDiagnostic(fixture),
                        () -> {
                            var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airAlarmMeter(
                                    fixture.level(), fixture.alarm());
                            if (!lease.entityTicking(alarmPos) || !player.isRegisteredAndAlive()
                                    || !meter.known() || meter.deliveredWatts() != 100) return false;
                            executeAdmissionAssertions(h, fixture, player, owner, alarmPos);
                            return true;
                        }, h::succeed);
            } catch (Throwable failure) { cleanup.run(); h.fail("Air alarm workspace player readiness: " + failure); }
        }, alarmPos);
    }

    private static void executeAdmissionAssertions(GameTestHelper h, AirAlarmPowerFixture.Setup fixture,
                                                   RegisteredServerPlayerFixture player,
                                                   RegisteredPlayerReadyDriver.Active owner,
                                                   net.minecraft.core.BlockPos alarmPos) {
                    try {
                        var meter = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airAlarmMeter(fixture.level(), fixture.alarm());
                        if (!meter.known() || meter.deliveredWatts() != 100)
                            throw new GameTestAssertException("fixture alarm lacks fresh 100 W: " + meter);
                        var hands = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(player.player(),
                                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(owner.epoch(), 2));
                        var hit = RegisteredPlayerReadyDriver.aimAt(owner.body(), fixture.level(), alarmPos);
                        var use = new BodyBlockUseRequest(owner.epoch(), 1, hands.revision(), hands.hands().getFirst().id(), null,
                                alarmPos, hit.getDirection(), false, BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
                        require(AuthenticatedBlockUseDriver.use(player.player(), use)
                                == com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult.Outcome.NO_CHANGE,
                                "registered admitted activation opens alarm workspace");
                        var session = AirAlarmMenuService.workspaceSession(player.player(), alarmPos);
                        require(session != null && !(player.player().containerMenu instanceof AirAlarmMenu),
                                "owned alarm session exists without replacing active container");
                        var query = new AirAlarmControlRequest(0, session, 1, new java.util.UUID(0, 0), 0,
                                AirAlarmControlRequest.Operation.QUERY, false, 0);
                        var snapshot = AirAlarmMenuService.apply(player.player(), query);
                        require(snapshot.accepted() && snapshot.session().equals(session) && snapshot.powered(),
                                "session query returns matching authoritative powered snapshot");
                        AirAlarmMenuService.close(player.player(), new AirAlarmSessionClose(session));
                        require(AirAlarmMenuService.workspaceSession(player.player(), alarmPos) == null
                                        && !AirAlarmMenuService.apply(player.player(), new AirAlarmControlRequest(0, session, 2,
                                        new java.util.UUID(0, 0), 0, AirAlarmControlRequest.Operation.QUERY, false, 0)).accepted(),
                                "closed session no longer queries or controls");
                    } catch (Throwable failure) { throw new GameTestAssertException("Air alarm workspace admission: " + failure); }
    }

    private static String meterDiagnostic(AirAlarmPowerFixture.Setup fixture) {
        return com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airAlarmMeter(
                fixture.level(), fixture.alarm()) + "; "
                + com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(fixture.level());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
