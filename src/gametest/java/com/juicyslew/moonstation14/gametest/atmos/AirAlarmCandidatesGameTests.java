package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmCandidates;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirAlarmCandidatesGameTests {
    private AirAlarmCandidatesGameTests() { }

    // The query is level-wide; nearby concurrent fixtures must not contribute indexed vents.
    @GameTest(template = "empty", batch = "air_alarm_candidates_isolated")
    public static void queryIsSortedBoundedAndDoesNotPair(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(origin.north(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        level.setBlock(origin, ModBlocks.AIR_ALARM.get().defaultBlockState(), 3);
        AirAlarmBlockEntity alarm = (AirAlarmBlockEntity) level.getBlockEntity(origin);
        if (alarm == null || alarm.prototype() == null || alarm.prototype().deviceList().maxDevices() != 32)
            throw new AssertionError("resolved alarm DeviceList must limit results to 32");
        BlockPos near = origin.east();
        BlockPos farther = origin.east(2);
        BlockPos outside = origin.east(17);
        level.setBlock(farther, ModBlocks.GAS_SCRUBBER.get().defaultBlockState(), 3);
        level.setBlock(near, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        level.setBlock(outside, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        // The first query must discover identities even before the placement tick's onLoad.
        checkCandidates(helper, level, alarm, origin, near, farther, outside);
    }

    private static void checkCandidates(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
                                        BlockPos origin, BlockPos near, BlockPos farther, BlockPos outside) {
        VentilationDeviceBlockEntity nearBE = (VentilationDeviceBlockEntity) level.getBlockEntity(near);
        var before = alarm.deviceLinks();
        var candidates = AirAlarmCandidates.query(level, alarm);
        if (nearBE == null || nearBE.deviceId() == null || candidates.size() != 2
                || !candidates.get(0).pos().equals(near) || !candidates.get(1).pos().equals(farther)
                || !candidates.get(0).deviceId().equals(nearBE.deviceId())
                || candidates.get(0).kind() != VentilationDeviceConfig.Kind.VENT
                || candidates.get(1).kind() != VentilationDeviceConfig.Kind.SCRUBBER
                || candidates.stream().anyMatch(AirAlarmCandidates.Candidate::linked)
                || !alarm.deviceLinks().equals(before) || !candidates.equals(AirAlarmCandidates.query(level, alarm)))
            throw new AssertionError("bounded indexed read must be deterministic, exact, and read-only: "
                    + "origin=" + origin + ", near=" + near + ", farther=" + farther
                    + ", outside=" + outside + ", candidates=" + candidates
                    + ", nearId=" + (nearBE == null ? null : nearBE.deviceId())
                    + ", nearState=" + level.getBlockState(near) + ", nearEntity=" + level.getBlockEntity(near)
                    + ", fartherState=" + level.getBlockState(farther) + ", fartherEntity=" + level.getBlockEntity(farther)
                    + ", linksBefore=" + before + ", linksAfter=" + alarm.deviceLinks());
        if (!alarm.addDeviceLink(nearBE)) throw new AssertionError("explicit link failed");
        var linked = AirAlarmCandidates.query(level, alarm);
        if (!linked.get(0).linked() || linked.get(1).linked() || alarm.deviceLinks().size() != 1)
            throw new AssertionError("only the explicit identity should be linked");
        level.setBlock(near, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(near, ModBlocks.GAS_VENT.get().defaultBlockState(), 3);
        checkReplacement(helper, level, alarm, origin, near);
    }

    private static void checkReplacement(GameTestHelper helper, ServerLevel level, AirAlarmBlockEntity alarm,
                                         BlockPos origin, BlockPos near) {
        if (AirAlarmCandidates.query(level, alarm).stream().anyMatch(AirAlarmCandidates.Candidate::linked))
            throw new AssertionError("replacement must not inherit link status");
        BlockPos unloaded = origin.offset(1000000, 0, 1000000);
        if (level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) != null)
            throw new AssertionError("test requires unloaded chunk");
        PowerRuntime.ventilationDeviceChanged(level, unloaded);
        AirAlarmCandidates.query(level, alarm);
        if (level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) != null)
            throw new AssertionError("candidate lookup force-loaded a chunk");
        PowerRuntime.ventilationDeviceRemoved(level, unloaded);
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
        if (!AirAlarmCandidates.query(level, alarm).isEmpty())
            throw new AssertionError("removed alarm must fail closed");
        helper.succeed();
    }
}
