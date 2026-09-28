package com.juicyslew.moonstation14.ms14.power.cable;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.power.cable.network.CableVisualServerHooks;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-owned mutation boundary for sparse host-face cable records. */
public final class CableStorage {
    private CableStorage() { }

    public static CableTier get(Level level, BlockPos pos, Direction face) {
        if (level.isClientSide) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return null;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data != null && level instanceof ServerLevel serverLevel) pruneChangedHost(serverLevel, pos, chunk, data);
        if (!eligible(level, pos, face)) return null;
        data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        return data == null ? null : data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face);
    }

    public static boolean has(Level level, BlockPos pos, Direction face, CableTier tier) {
        if (level.isClientSide) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data != null && level instanceof ServerLevel serverLevel) pruneChangedHost(serverLevel, pos, chunk, data);
        if (!eligible(level, pos, face)) return false;
        data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        return data != null && data.contains(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier);
    }

    public static boolean place(ServerLevel level, BlockPos pos, Direction face, CableTier tier) {
        if (!eligible(level, pos, face)) return false;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return false;
        CableChunkData existing = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (existing != null) pruneChangedHost(level, pos, chunk, existing);
        existing = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (existing != null && existing.contains(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier)) return false;
        CableChunkData data = existing == null ? chunk.getData(ModDataAttachments.POWER_CABLE_CHUNK.get()) : existing;
        if (!data.put(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier, hostIdentity(level, pos))) return false;
        chunk.setUnsaved(true);
        CableVisualServerHooks.noteChanged(level, chunk.getPos());
        PowerGraphService.noteChanged(level, chunk.getPos());
        return true;
    }

    public static CableTier remove(ServerLevel level, BlockPos pos, Direction face) {
        if (!eligible(level, pos, face)) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return null;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data == null) return null;
        pruneChangedHost(level, pos, chunk, data);
        data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data == null) return null;
        CableTier tier = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face);
        if (tier == null || !data.remove(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face)) return null;
        chunk.setUnsaved(true);
        CableVisualServerHooks.noteChanged(level, chunk.getPos());
        PowerGraphService.noteChanged(level, chunk.getPos());
        return tier;
    }

    public static CableTier remove(ServerLevel level, BlockPos pos, Direction face, CableTier tier) {
        if (!eligible(level, pos, face)) return null;
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return null;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data == null) return null;
        pruneChangedHost(level, pos, chunk, data);
        data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data == null || !data.remove(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier)) return null;
        chunk.setUnsaved(true);
        CableVisualServerHooks.noteChanged(level, chunk.getPos());
        PowerGraphService.noteChanged(level, chunk.getPos());
        return tier;
    }

    /** Removes all records from an owned host during actual block replacement, not state-property edits. */
    public static void removeHost(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = loadedChunk(level, pos);
        if (chunk == null) return;
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data != null && data.removeHost(pos.getX() & 15, pos.getY(), pos.getZ() & 15) > 0) {
            chunk.setUnsaved(true);
            CableVisualServerHooks.noteChanged(level, chunk.getPos());
            PowerGraphService.noteChanged(level, chunk.getPos());
        }
    }

    public static boolean eligible(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BARRIER)) return false;
        return state.isFaceSturdy(level, pos, face)
                && Shapes.block().equals(state.getCollisionShape(level, pos));
    }

    /** Validates one persisted record against an already-loaded host during a graph refresh. */
    public static boolean validateGraphRecord(ServerLevel level, LevelChunk chunk, BlockPos pos,
                                              Direction face, CableTier tier, String host) {
        CableChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.POWER_CABLE_CHUNK.get());
        if (data == null || data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier) != tier
                || !java.util.Objects.equals(data.host(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier), host)) {
            return false;
        }
        String currentHost = hostIdentity(level, pos);
        if (!host.equals(currentHost)) {
            pruneChangedHost(level, pos, chunk, data);
            return false;
        }
        if (eligible(level, pos, face)) return true;
        if (data.remove(pos.getX() & 15, pos.getY(), pos.getZ() & 15, face, tier)) {
            chunk.setUnsaved(true);
            CableVisualServerHooks.noteChanged(level, chunk.getPos());
            PowerGraphService.noteChanged(level, chunk.getPos());
        }
        return false;
    }

    private static void pruneChangedHost(ServerLevel level, BlockPos pos, LevelChunk chunk, CableChunkData data) {
        if (data.removeHostUnlessIdentity(pos.getX() & 15, pos.getY(), pos.getZ() & 15, hostIdentity(level, pos)) > 0) {
            chunk.setUnsaved(true);
            CableVisualServerHooks.noteChanged(level, chunk.getPos());
            PowerGraphService.noteChanged(level, chunk.getPos());
        }
    }

    private static String hostIdentity(Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }

    private static LevelChunk loadedChunk(Level level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk levelChunk ? levelChunk : null;
    }
}
