package com.juicyslew.moonstation14.ms14.atmos.piping.alarm;

import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** On-demand, read-only local menu discovery; not an SS14 frequency or wired device network. */
public final class AirAlarmCandidates {
    public static final int RANGE_BLOCKS = 16;
    public static final int MAX_VISITED_INDEX_ENTRIES = 4096;
    private static final int MAX_SHOWN = 32;

    public record Candidate(UUID deviceId, VentilationDeviceConfig.Kind kind, BlockPos pos, boolean linked) {
        public Candidate { pos = pos.immutable(); }
    }

    private AirAlarmCandidates() { }

    /** Caller supplies the exact alarm BE, never a client-provided position. No chunk requests or link changes. */
    public static List<Candidate> query(ServerLevel level, AirAlarmBlockEntity alarm) {
        if (level == null || alarm == null || !level.getServer().isSameThread()
                || alarm.getLevel() != level || alarm.isRemoved() || !alarm.deviceLinksValid()
                || alarm.prototype() == null || alarm.prototype().deviceList().maxDevices() != MAX_SHOWN)
            return List.of();
        BlockPos origin = alarm.getBlockPos();
        LevelChunk alarmChunk = loadedFullChunk(level, origin);
        if (alarmChunk == null || alarmChunk.getBlockEntity(origin) != alarm
                || !(alarmChunk.getBlockState(origin).getBlock() instanceof AirAlarmBlock)) return List.of();

        List<BlockPos> indexed = PowerRuntime.indexedVentilationCandidates(level, MAX_VISITED_INDEX_ENTRIES);
        List<AirAlarmDeviceLinks.Link> links = alarm.deviceLinks();
        List<Candidate> found = new ArrayList<>();
        for (BlockPos pos : indexed) {
            long dx = (long) pos.getX() - origin.getX();
            long dy = (long) pos.getY() - origin.getY();
            long dz = (long) pos.getZ() - origin.getZ();
            if (Math.abs(dx) > RANGE_BLOCKS || Math.abs(dy) > RANGE_BLOCKS
                    || Math.abs(dz) > RANGE_BLOCKS
                    || dx * dx + dy * dy + dz * dz > RANGE_BLOCKS * RANGE_BLOCKS) continue;
            LevelChunk chunk = loadedFullChunk(level, pos);
            if (chunk == null || !(chunk.getBlockState(pos).getBlock() instanceof VentilationDeviceBlock block)
                    || !(chunk.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity device)
                    || device.isRemoved() || device.getLevel() != level || device.deviceId() == null
                    || device.kind() != block.kind() || !device.settingsValid()) continue;
            boolean linked = links.contains(new AirAlarmDeviceLinks.Link(level.dimension(), pos,
                    block.kind(), device.deviceId()));
            found.add(new Candidate(device.deviceId(), block.kind(), pos, linked));
        }
        found.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.pos().distSqr(origin))
                .thenComparingInt(candidate -> candidate.pos().getX())
                .thenComparingInt(candidate -> candidate.pos().getY())
                .thenComparingInt(candidate -> candidate.pos().getZ())
                .thenComparing(candidate -> candidate.deviceId().toString()));
        return List.copyOf(found.subList(0, Math.min(MAX_SHOWN, found.size())));
    }

    private static LevelChunk loadedFullChunk(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL) ? chunk : null;
    }
}
