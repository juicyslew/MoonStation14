package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmDeviceLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirAlarmDeviceLinksGameTests {
    private AirAlarmDeviceLinksGameTests() { }

    @GameTest(template = "empty", batch = "air_alarm_device_links_isolated")
    public static void exactLiveIdentityAndReplacement(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos alarmPos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ventPos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(alarmPos.north(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(alarmPos, ModBlocks.AIR_ALARM.get().defaultBlockState(), 3);
        level.setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        // Linking must work in the placement tick, before onLoad runs.
        checkLinks(helper, level, alarmPos, ventPos);
    }

    private static void checkLinks(GameTestHelper helper, ServerLevel level, BlockPos alarmPos, BlockPos ventPos) {
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(alarmPos);
        VentilationDeviceBlockEntity vent = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        if (alarm == null || vent == null || vent.deviceId() == null || !alarm.addDeviceLink(vent))
            throw new AssertionError("server must create and pair exact live identity: alarm=" + alarm
                    + ", alarmState=" + level.getBlockState(alarmPos) + ", vent=" + vent
                    + ", ventState=" + level.getBlockState(ventPos)
                    + ", ventEntity=" + level.getBlockEntity(ventPos));
        var link = alarm.deviceLinks().getFirst();
        vent.onLoad();
        if (!link.deviceId().equals(vent.deviceId()))
            throw new AssertionError("onLoad must not replace an identity created on first read");
        var savedVent = vent.saveWithoutMetadata(level.registryAccess());
        var savedAlarm = alarm.saveWithoutMetadata(level.registryAccess());
        var reloadedVent = new VentilationDeviceBlockEntity(ventPos, level.getBlockState(ventPos));
        reloadedVent.loadWithComponents(savedVent, level.registryAccess());
        var reloadedAlarm = new AirAlarmBlockEntity(alarmPos, level.getBlockState(alarmPos));
        reloadedAlarm.loadWithComponents(savedAlarm, level.registryAccess());
        if (!vent.deviceId().equals(reloadedVent.deviceId()) || !alarm.deviceLinks().equals(reloadedAlarm.deviceLinks()))
            throw new AssertionError("saved identities did not survive roundtrip");
        var unbound = new VentilationDeviceBlockEntity(ventPos, level.getBlockState(ventPos));
        if (unbound.deviceId() != null || unbound.saveWithoutMetadata(level.registryAccess()).hasUUID("VentilationDeviceId"))
            throw new AssertionError("detached entity must not generate or persist an identity");
        if (alarm.resolveDeviceLink(link) != vent || alarm.addDeviceLink(vent))
            throw new AssertionError("identity resolution and duplicate protection");
        if (AirAlarmDeviceLinks.resolve(level, new AirAlarmDeviceLinks.Link(level.dimension(), ventPos,
                VentilationDeviceConfig.Kind.SCRUBBER, link.deviceId())) != null
                || AirAlarmDeviceLinks.resolve(level, new AirAlarmDeviceLinks.Link(level.dimension(), ventPos,
                link.kind(), UUID.randomUUID())) != null
                || AirAlarmDeviceLinks.resolve(level, new AirAlarmDeviceLinks.Link(net.minecraft.world.level.Level.NETHER,
                ventPos, link.kind(), link.deviceId())) != null)
            throw new AssertionError("wrong kind/dimension/UUID resolved");
        level.setBlock(ventPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(ventPos, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        checkReplacement(helper, level, alarm, ventPos, link);
    }

    private static void checkReplacement(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
                                         BlockPos ventPos, AirAlarmDeviceLinks.Link link) {
        var replacement = (VentilationDeviceBlockEntity) level.getBlockEntity(ventPos);
        if (replacement == null || replacement.deviceId() == null || alarm.resolveDeviceLink(link) != null
                || link.deviceId().equals(replacement.deviceId()))
            throw new AssertionError("replaced entity retained old identity or did not initialize immediately");
        // getChunkNow must not create this far-away chunk.
        BlockPos unloaded = ventPos.offset(1000000, 0, 1000000);
        if (level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) != null)
            throw new AssertionError("test requires unloaded chunk");
        if (AirAlarmDeviceLinks.resolve(level, new AirAlarmDeviceLinks.Link(level.dimension(), unloaded,
                link.kind(), link.deviceId())) != null
                || level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) != null)
            throw new AssertionError("resolver loaded an unloaded chunk");
        helper.succeed();
    }
}
