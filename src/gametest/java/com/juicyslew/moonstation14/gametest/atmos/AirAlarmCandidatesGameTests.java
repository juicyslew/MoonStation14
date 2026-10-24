package com.juicyslew.moonstation14.gametest.atmos;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
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
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

@GameTestTags({"air-alarm", "ventilation"})
@PrefixGameTestTemplate(false)
public final class AirAlarmCandidatesGameTests {
    private AirAlarmCandidatesGameTests() { }

    // The query is level-wide; nearby concurrent fixtures must not contribute indexed vents.
@GameTest(templateNamespace = "moonstation14", template = "empty", batch = "air_alarm_candidates_isolated")
    @GameTestTags("air-alarm-candidate-query")
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
        int nearIndex = indexOf(candidates, near);
        int fartherIndex = indexOf(candidates, farther);
        if (nearBE == null || nearBE.deviceId() == null || candidates.size() < 2 || candidates.size() > 32
                || nearIndex < 0 || fartherIndex <= nearIndex
                || !candidates.get(nearIndex).deviceId().equals(nearBE.deviceId())
                || candidates.get(nearIndex).kind() != VentilationDeviceConfig.Kind.VENT
                || candidates.get(fartherIndex).kind() != VentilationDeviceConfig.Kind.SCRUBBER
                || candidates.stream().anyMatch(candidate -> candidate.pos().equals(outside))
                || candidates.stream().anyMatch(candidate -> candidate.pos().distSqr(origin)
                        > AirAlarmCandidates.RANGE_BLOCKS * AirAlarmCandidates.RANGE_BLOCKS)
                || !hasUniquePositionsAndIdentities(candidates)
                || !isSorted(candidates, origin)
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

    private static boolean hasUniquePositionsAndIdentities(java.util.List<AirAlarmCandidates.Candidate> candidates) {
        Set<BlockPos> positions = new HashSet<>();
        Set<java.util.UUID> identities = new HashSet<>();
        for (AirAlarmCandidates.Candidate candidate : candidates)
            if (!positions.add(candidate.pos()) || !identities.add(candidate.deviceId())) return false;
        return true;
    }

    private static int indexOf(java.util.List<AirAlarmCandidates.Candidate> candidates, BlockPos pos) {
        for (int i = 0; i < candidates.size(); i++)
            if (candidates.get(i).pos().equals(pos)) return i;
        return -1;
    }

    private static boolean isSorted(java.util.List<AirAlarmCandidates.Candidate> candidates, BlockPos origin) {
        var comparator = Comparator.comparingDouble((AirAlarmCandidates.Candidate candidate) -> candidate.pos().distSqr(origin))
                .thenComparingInt(candidate -> candidate.pos().getX())
                .thenComparingInt(candidate -> candidate.pos().getY())
                .thenComparingInt(candidate -> candidate.pos().getZ())
                .thenComparing(candidate -> candidate.deviceId().toString());
        for (int i = 1; i < candidates.size(); i++)
            if (comparator.compare(candidates.get(i - 1), candidates.get(i)) > 0) return false;
        return true;
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
