package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosDevicePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"device-network"})
@PrefixGameTestTemplate(false)
public final class DeviceAlarmWorkflowGameTests {
    private DeviceAlarmWorkflowGameTests() { }

    @GameTest(templateNamespace = "moonstation14", batch = "device_alarm_native_workflow", template = "power_receiver_tall_clear", timeoutTicks = 320)
    @GameTestTags({"shuttle-owner-regression"})
    public static void poweredAlarmQueuedSyncReceivesVentState(GameTestHelper helper) {
        var fixture = AirAlarmPowerFixture.place(helper);
        BlockPos ventPos = fixture.alarmPos().east();
        fixture.level().setBlock(ventPos.east(), Blocks.STONE.defaultBlockState(), 3);
        fixture.level().setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.WEST), 3);
        var vent = (VentilationDeviceBlockEntity) fixture.level().getBlockEntity(ventPos);
        require(vent != null && fixture.alarm().addDeviceLink(vent), "link exact live vent identity");
        awaitPower(helper, fixture.alarm(), vent);
    }

    private static void awaitPower(GameTestHelper helper, AirAlarmBlockEntity alarm,
                                   VentilationDeviceBlockEntity vent) {
        var level = (net.minecraft.server.level.ServerLevel) alarm.getLevel();
        PowerFixtureReadiness.await(helper, level, 220,
                () -> meterReady(alarm) && vent.settingsValid() && endpointReady(alarm, vent),
                () -> diagnostic(alarm, vent), () -> requestSyncOnce(helper, alarm, vent));
    }

    private static boolean endpointReady(AirAlarmBlockEntity alarm, VentilationDeviceBlockEntity vent) {
        var level = (net.minecraft.server.level.ServerLevel) alarm.getLevel();
        var transport = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level).transport();
        var aid = new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(alarm.controllerId());
        var vid = new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(vent.deviceId());
        var alarmEndpoint = transport.lookup(aid);
        var ventEndpoint = transport.lookup(vid);
        return alarmEndpoint != null && ventEndpoint != null && alarmEndpoint.available() && ventEndpoint.available()
                && alarmEndpoint.powered() && ventEndpoint.powered()
                && alarmEndpoint.composition().network().filter(n -> transport.isConnected(aid, n.networkId())).isPresent()
                && ventEndpoint.composition().network().filter(n -> transport.isConnected(vid, n.networkId())).isPresent();
    }

    private static boolean meterReady(AirAlarmBlockEntity alarm) {
        var level = (net.minecraft.server.level.ServerLevel) alarm.getLevel();
        var alarmMeter = PowerRuntime.airAlarmMeter(level, alarm);
        // DeviceNet Sync reads settings; it does not perform gas work or require a paid vent interval.
        return alarmMeter.known() && alarmMeter.deliveredWatts() == 100;
    }

    private static void requestSyncOnce(GameTestHelper helper, AirAlarmBlockEntity alarm,
                                        VentilationDeviceBlockEntity vent) {
        var powerHistory = new java.util.ArrayList<String>(20);
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.observeServerTicks(helper, 20, event -> {
            var level = (net.minecraft.server.level.ServerLevel) alarm.getLevel();
            var meter = PowerRuntime.airAlarmMeter(level, alarm);
            powerHistory.add("tick=" + event.getServer().getTickCount() + ":known=" + meter.known()
                    + ":watts=" + meter.deliveredWatts() + ":currentGeneration="
                    + com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level));
        }, () -> { });
        alarm.requestDeviceSync();
        helper.runAfterDelay(5, () -> {
            var settings = vent.config().settings();
            var expected = AtmosDevicePayload.State.from(settings);
            require(alarm.observedStates().containsValue(expected), "queued Sync observes actual vent settings; "
                    + diagnostic(alarm, vent) + " powerHistory(server-tick-post)=" + powerHistory + " expected=" + expected);
            var runtime = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(
                    (net.minecraft.server.level.ServerLevel) alarm.getLevel());
            var alarmEndpoint = runtime.transport().lookup(new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(alarm.controllerId()));
            var ventEndpoint = runtime.transport().lookup(new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(vent.deviceId()));
            var source = alarmEndpoint.composition().network().orElseThrow();
            var target = ventEndpoint.composition().network().orElseThrow();
            var transport = runtime.transport();
            var command = new AtmosDevicePayload.SetState(settings.revision(), false, settings.externalPressureBoundKPa(),
                    settings.targetPressureChangeKPaPerSecond(), settings.targetPressureKPa(), settings.volumeRateM3PerSecond(),
                    settings.scrubberMode(), settings.filteredGases(), settings.selectedLayer());
            require(transport.enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                    alarmEndpoint.identity(), 4, 1621, source.address(), target.address(), false, command)), "enqueue disable");
            require(vent.config().settings().configuredEnabled(), "queued disable has not applied synchronously");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                var afterDisable = vent.config().settings();
                    require(!afterDisable.configuredEnabled() && afterDisable.revision() == settings.revision() + 1,
                        "actual vent endpoint applies disable; " + diagnostic(alarm, vent)
                                + " powerHistory(server-tick-post)=" + powerHistory);
                alarm.requestDeviceSync();
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                    require(alarm.observedStates().containsValue(AtmosDevicePayload.State.from(afterDisable)),
                            "alarm receives disabled state through vent reply; " + diagnostic(alarm, vent)
                                    + " powerHistory(server-tick-post)=" + powerHistory);
                    enqueueEnable(helper, alarm, vent, alarmEndpoint, ventEndpoint, powerHistory);
                });
            });
        });
    }

    private static String diagnostic(AirAlarmBlockEntity alarm, VentilationDeviceBlockEntity vent) {
        var level = (net.minecraft.server.level.ServerLevel) alarm.getLevel();
        var meter = PowerRuntime.airAlarmMeter(level, alarm);
        var runtime = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level);
        var alarmIdentity = new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(alarm.controllerId());
        var ventIdentity = new com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity(vent.deviceId());
        var alarmEndpoint = runtime.transport().lookup(alarmIdentity);
        var endpoint = runtime.transport().lookup(ventIdentity);
        var alarmNetwork = alarmEndpoint == null ? null : alarmEndpoint.composition().network().orElse(null);
        var ventNetwork = endpoint == null ? null : endpoint.composition().network().orElse(null);
        var ventMeter = PowerRuntime.ventilationMeter(level, vent);
        return "gameTime=" + level.getGameTime() + " alarmId=" + alarm.controllerId() + " alarmPower=" + meter
                + " ventPower=" + ventMeter + " ventTopology=" + PowerRuntime.ventilationMeterDiagnostic(level, vent)
                + " ventId=" + vent.deviceId() + " ventRevision=" + vent.config().settings().revision()
                + " ventRequested=" + vent.config().settings().configuredEnabled()
                + " ventSettings=" + vent.config().settings() + " ventEndpoint=" + endpoint
                + " ventAvailable=" + (endpoint != null && endpoint.available())
                + " ventPowered=" + (endpoint != null && endpoint.powered())
                + " alarmEndpointAvailable=" + (alarmEndpoint != null && alarmEndpoint.available())
                + " alarmNetwork=" + alarmNetwork + " ventNetwork=" + ventNetwork
                + " alarmConnected=" + (alarmNetwork != null && runtime.transport().isConnected(alarmIdentity, alarmNetwork.networkId()))
                + " ventConnected=" + (ventNetwork != null && runtime.transport().isConnected(ventIdentity, ventNetwork.networkId()))
                + " queuedNetworkSteps=" + runtime.transport().queuedPackets()
                + " linked=" + alarm.deviceLinks() + " observed=" + alarm.observedStates();
    }

    private static void enqueueEnable(GameTestHelper helper, AirAlarmBlockEntity alarm, VentilationDeviceBlockEntity vent,
                                     com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint alarmEndpoint,
                                     com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint ventEndpoint,
                                     java.util.List<String> powerHistory) {
        var settings = vent.config().settings();
        var target = ventEndpoint.composition().network().orElseThrow();
        var source = alarmEndpoint.composition().network().orElseThrow();
        var command = new AtmosDevicePayload.SetState(settings.revision(), true, settings.externalPressureBoundKPa(),
                settings.targetPressureChangeKPaPerSecond(), settings.targetPressureKPa(), settings.volumeRateM3PerSecond(),
                settings.scrubberMode(), settings.filteredGases(), settings.selectedLayer());
        var runtime = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(
                (net.minecraft.server.level.ServerLevel) alarm.getLevel());
        require(runtime.transport().enqueue(new com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkPacket(
                alarmEndpoint.identity(), 4, 1621, source.address(), target.address(), false, command)), "enqueue re-enable");
        helper.runAfterDelay(5, () -> {
            require(vent.config().settings().configuredEnabled(), "actual vent endpoint applies re-enable; "
                    + diagnostic(alarm, vent) + " powerHistory(server-tick-post)=" + powerHistory);
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
