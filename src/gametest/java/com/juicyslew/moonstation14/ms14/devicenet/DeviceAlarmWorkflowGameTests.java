package com.juicyslew.moonstation14.ms14.devicenet;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.atmos.AirAlarmPowerFixture;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
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

    @GameTest(templateNamespace = "moonstation14", template = "power_receiver_tall_clear", batch = "powered_alarm_device_sync_workflow", timeoutTicks = 320)
    public static void poweredAlarmQueuedSyncReceivesVentState(GameTestHelper helper) {
        var fixture = AirAlarmPowerFixture.place(helper);
        BlockPos ventPos = fixture.alarmPos().east();
        fixture.level().setBlock(ventPos.east(), Blocks.STONE.defaultBlockState(), 3);
        fixture.level().setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(VentilationDeviceBlock.FACING, Direction.WEST), 3);
        var vent = (VentilationDeviceBlockEntity) fixture.level().getBlockEntity(ventPos);
        require(vent != null && fixture.alarm().addDeviceLink(vent), "link exact live vent identity");
        awaitPower(helper, fixture.alarm(), vent, 0);
    }

    private static void awaitPower(GameTestHelper helper, AirAlarmBlockEntity alarm,
                                   VentilationDeviceBlockEntity vent, int waited) {
        var meter = PowerRuntime.airAlarmMeter((net.minecraft.server.level.ServerLevel) alarm.getLevel(), alarm);
        if (!meter.known() || meter.deliveredWatts() != 100) {
            require(waited < 220, "alarm waits for paid 100 W solve");
            helper.runAfterDelay(5, () -> awaitPower(helper, alarm, vent, waited + 5));
            return;
        }
        alarm.requestDeviceSync();
        helper.runAfterDelay(5, () -> {
            var settings = vent.config().settings();
            var expected = AtmosDevicePayload.State.from(settings);
            require(alarm.observedStates().containsValue(expected), "queued Sync observes actual vent settings");
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
                        "actual vent endpoint applies disable");
                alarm.requestDeviceSync();
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 5, () -> {
                    require(alarm.observedStates().containsValue(AtmosDevicePayload.State.from(afterDisable)),
                            "alarm receives disabled state through vent reply");
                    enqueueEnable(helper, alarm, vent, alarmEndpoint, ventEndpoint);
                });
            });
        });
    }

    private static void enqueueEnable(GameTestHelper helper, AirAlarmBlockEntity alarm, VentilationDeviceBlockEntity vent,
                                     com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint alarmEndpoint,
                                     com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint ventEndpoint) {
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
            require(vent.config().settings().configuredEnabled(), "actual vent endpoint applies re-enable");
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
