package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.sealedwall.SealedWallHostGenerations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-only persistence/admission boundary for sealed-wall lanes. It does not create gas or graph state. */
public final class LoadedSealedWallSegment {
    public enum RollbackResult { ROLLED_BACK, NOT_APPLICABLE, RECOVERY_REQUIRED }
    private static Runnable verificationHook;
    private LoadedSealedWallSegment() { }

    /** Test-only seam for exercising post-write admission rollback. */
    static void setVerificationHookForTests(Runnable hook) { verificationHook = hook; }

    /** Removes an exact newly-created record only while its host incarnation and admission shape remain unchanged. */
    public static RollbackResult rollbackFreshRecord(ServerLevel level, BlockPos pos, PipeTopology.Layer lane,
                                                       UUID id, UUID expectedHostGeneration) {
        if (level == null || pos == null || lane == null || id == null || expectedHostGeneration == null
                || !level.getServer().isSameThread()) return RollbackResult.NOT_APPLICABLE;
        LevelChunk chunk = fullChunk(level, pos);
        if (chunk == null || !eligible(level, pos) || chunk.getBlockEntity(pos) != null) return RollbackResult.NOT_APPLICABLE;
        PipeChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        SealedWallHostGenerations generations = chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        if (data == null || generations == null || !generations.active(pos.asLong()).filter(expectedHostGeneration::equals).isPresent())
            return RollbackResult.NOT_APPLICABLE;
        int x = pos.getX() & 15, z = pos.getZ() & 15;
        PipeChunkData.Record record = data.get(x, pos.getY(), z, lane);
        if (record == null || !record.id().equals(id) || record.kind() != PipeTopology.Kind.STRAIGHT_SEALED_WALL
                || !record.hostIncarnation().equals(expectedHostGeneration.toString()) || record.ports() == 0
                || !matchingLive(level, pos, lane).filter(record::equals).isPresent()) return RollbackResult.NOT_APPLICABLE;
        // The host generation is intentionally retained: it may have predated this lane or serve another lane.
        if (!data.remove(x, pos.getY(), z, lane)) return RollbackResult.RECOVERY_REQUIRED;
        chunk.setUnsaved(true);
        return RollbackResult.ROLLED_BACK;
    }

    public static Optional<PipeChunkData.Record> prepareOne(ServerLevel level, BlockPos clicked, Direction face,
                                                             PipeTopology.Layer lane, UUID freshId) {
        Objects.requireNonNull(level); Objects.requireNonNull(clicked); Objects.requireNonNull(face);
        Objects.requireNonNull(lane); Objects.requireNonNull(freshId);
        LevelChunk chunk = fullChunk(level, clicked);
        if (chunk == null || !level.getServer().isSameThread() || !eligible(level, clicked)) return Optional.empty();
        BlockEntity be = chunk.getBlockEntity(clicked);
        if (be != null) return Optional.empty();

        int x = clicked.getX() & 15, z = clicked.getZ() & 15;
        PipeChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        PipeChunkData.Record prior = data == null ? null : data.get(x, clicked.getY(), z, lane);
        if (prior != null) return Optional.empty();
        if (data == null) data = chunk.getData(ModDataAttachments.PIPE_CHUNK.get());
        if (data.size() >= PipeChunkData.MAX_RECORDS || data.snapshot().stream().anyMatch(r -> r.id().equals(freshId))) return Optional.empty();

        SealedWallHostGenerations generations = chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        boolean createdGenerations = generations == null;
        if (createdGenerations) generations = chunk.getData(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        Optional<UUID> current = generations.active(clicked.asLong());
        boolean enrolled = false;
        UUID incarnation;
        if (current.isPresent()) incarnation = current.get();
        else {
            // An invalidated tombstone is occupied and must not be revived.
            incarnation = UUID.randomUUID();
            enrolled = generations.registerIfAbsent(clicked.asLong(), incarnation);
            if (!enrolled) return Optional.empty();
        }

        var admittedState = chunk.getBlockState(clicked);
        String host = BuiltInRegistries.BLOCK.getKey(admittedState.getBlock()).toString();
        int ports = PipeChunkData.Record.portMask(java.util.List.of(face, face.getOpposite()));
        PipeChunkData.Record record = new PipeChunkData.Record(x, clicked.getY(), z, freshId,
                PipeTopology.Kind.STRAIGHT_SEALED_WALL, ports, Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3,
                host, incarnation.toString(), lane);
        try {
            if (!data.add(record)) {
                if (enrolled) generations.removeActiveExact(clicked.asLong(), incarnation);
                return Optional.empty();
            }
            chunk.setUnsaved(true);
            if (verificationHook != null) verificationHook.run();
            if (chunk.getBlockState(clicked) != admittedState && !chunk.getBlockState(clicked).equals(admittedState))
                throw new IllegalStateException("sealed-wall host state changed during admission");
            Optional<PipeChunkData.Record> verified = matchingLive(level, clicked, lane).filter(record::equals);
            if (verified.isPresent()) return verified;
            rollback(data, generations, clicked, x, lane, record, enrolled, incarnation);
            return Optional.empty();
        } catch (RuntimeException failure) {
            if (failure instanceof PipeGasTransaction.RecoveryRequiredException) throw failure;
            rollback(data, generations, clicked, x, lane, record, enrolled, incarnation);
            return Optional.empty();
        }
    }

    private static void rollback(PipeChunkData data, SealedWallHostGenerations generations, BlockPos clicked,
                                 int x, PipeTopology.Layer lane, PipeChunkData.Record record,
                                 boolean enrolled, UUID incarnation) {
        PipeChunkData.Record present = data.get(x, clicked.getY(), clicked.getZ() & 15, lane);
        if (present != null && !present.equals(record))
            throw new PipeGasTransaction.RecoveryRequiredException("sealed-wall admission record was replaced; rollback unsafe", null, null);
        if (present != null && !data.remove(x, clicked.getY(), clicked.getZ() & 15, lane))
            throw new PipeGasTransaction.RecoveryRequiredException("sealed-wall admission record rollback failed", null, null);
        if (enrolled && !generations.removeActiveExact(clicked.asLong(), incarnation))
            throw new PipeGasTransaction.RecoveryRequiredException("sealed-wall generation rollback was not confirmed", null, null);
    }

    /** Read-only validation; stale records remain quarantined in persistence. */
    public static Optional<PipeChunkData.Record> matchingLive(ServerLevel level, BlockPos pos, PipeTopology.Layer lane) {
        if (level == null || pos == null || lane == null || !level.getServer().isSameThread()) return Optional.empty();
        LevelChunk chunk = fullChunk(level, pos);
        if (chunk == null || !eligible(level, pos) || chunk.getBlockEntity(pos) != null) return Optional.empty();
        PipeChunkData data = chunk.getExistingDataOrNull(ModDataAttachments.PIPE_CHUNK.get());
        SealedWallHostGenerations generations = chunk.getExistingDataOrNull(ModDataAttachments.SEALED_WALL_HOST_GENERATIONS.get());
        if (data == null || generations == null) return Optional.empty();
        PipeChunkData.Record record = data.get(pos.getX() & 15, pos.getY(), pos.getZ() & 15, lane);
        if (record == null || record.kind() != PipeTopology.Kind.STRAIGHT_SEALED_WALL
                || record.layer() != lane || record.ports() == 0 || Double.compare(record.volume(), Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3) != 0) return Optional.empty();
        String host = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString();
        return host.equals(record.host()) && generations.active(pos.asLong())
                .filter(id -> id.toString().equals(record.hostIncarnation())).isPresent()
                ? Optional.of(record) : Optional.empty();
    }

    private static boolean eligible(ServerLevel level, BlockPos pos) {
        return SealedWallHostEligibility.defaults().eligible(level, pos)
                && chunkState(level, pos) && level.getBlockEntity(pos) == null;
    }

    private static boolean chunkState(ServerLevel level, BlockPos pos) { return fullChunk(level, pos) != null; }

    private static LevelChunk fullChunk(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return null;
        var chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return chunk instanceof LevelChunk full ? full : null;
    }
}
