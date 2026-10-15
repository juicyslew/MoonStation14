package com.juicyslew.moonstation14.ms14.explosion.server;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.core.BlastKnockbackPolicy;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ExplosionData;
import com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Explicit server-side test adapter only. Not a gameplay trigger/API. */
public final class ServerExplosionService {
    public static final double FIXTURE_TOLERANCE = 2.0;
    private static final int MAX_ENTITIES_INSPECTED = 256;
    private static final int MAX_ENTITY_HITS = 128;
    private ServerExplosionService() { }

    public enum Status { APPLIED, PARTIAL, UNKNOWN_PROTOTYPE, CLIENT_LEVEL, INVALID_OR_STALE, PLAN_FAILED }
    public record Result(Status status, int visited, int breached, int mutated, int damaged) { }

    /** Executes only when explicitly invoked by trusted server code. */
    public static Result execute(ServerLevel level, BlockPos source, ResourceLocation prototypeId,
                                  BlastPlanner.Proposal proposal) {
        if (level == null || level.isClientSide()) return result(Status.CLIENT_LEVEL, 0);
        if (prototypeId == null) return result(Status.UNKNOWN_PROTOTYPE, 0);
        ExplosionData data = PrototypeRuntime.serverExplosions().get(prototypeId);
        if (data == null) return result(Status.UNKNOWN_PROTOTYPE, 0);
        if (source == null || proposal == null) return result(Status.INVALID_OR_STALE, 0);
        if (source.getY() < level.getMinBuildHeight() || source.getY() >= level.getMaxBuildHeight())
            return result(Status.INVALID_OR_STALE, 0);
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> observed = new LinkedHashMap<>();
        BlastPlanner.Plan plan;
        try {
            plan = BlastPlanner.planWithTolerance(new BlastPlanner.Pos(source.getX(), source.getY(), source.getZ()), proposal,
                    p -> sample(level, new BlockPos(p.x(), p.y(), p.z()), observed));
        } catch (RuntimeException exception) {
            return result(Status.PLAN_FAILED, 0);
        }
        if (!plan.successful()) return new Result(Status.PLAN_FAILED, plan.visited(), 0, 0, 0);

        for (var entry : observed.entrySet()) if (!Objects.equals(entry.getValue(), stateIfLoaded(level, entry.getKey())))
            return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);

        // Determine entities and their maximum occupied-voxel intensity before writes, matching SS14 ordering.
        Map<UUID, EntityHit> hits = new HashMap<>();
        int inspected = 0;
        try {
            for (var intensity : plan.intensities().entrySet()) {
                BlockPos pos = new BlockPos(intensity.getKey().x(), intensity.getKey().y(), intensity.getKey().z());
                if (!Objects.equals(observed.get(pos), stateIfLoaded(level, pos)))
                    return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);
                int remaining = MAX_ENTITIES_INSPECTED - inspected;
                ArrayList<LivingEntity> cellEntities = new ArrayList<>(Math.min(remaining + 1, 16));
                level.getEntities(EntityTypeTest.forClass(LivingEntity.class), new AABB(pos).inflate(0.001),
                        ServerExplosionService::eligibleForPhysicalHit, cellEntities, remaining + 1);
                inspected += cellEntities.size();
                if (inspected > MAX_ENTITIES_INSPECTED)
                    return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);
                for (LivingEntity entity : cellEntities) {
                    if (!intersects(entity.getBoundingBox(), pos)) continue;
                    EntityHit old = hits.get(entity.getUUID());
                    if (old == null) {
                        if (hits.size() >= MAX_ENTITY_HITS)
                            return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);
                        hits.put(entity.getUUID(), new EntityHit(entity, intensity.getValue()));
                    } else if (intensity.getValue() > old.intensity)
                        hits.put(entity.getUUID(), new EntityHit(entity, intensity.getValue()));
                }
            }
        } catch (RuntimeException exception) { return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0); }

        // Freeze and validate all scaled hit maps before the first world effect.
        Map<UUID, Map<String, Float>> damageByEntity = new HashMap<>();
        try {
            for (var entry : hits.entrySet()) {
                Map<String, Float> damage = new LinkedHashMap<>();
                for (var amount : data.damagePerIntensity().entrySet()) {
                    double scaled = amount.getValue() * entry.getValue().intensity;
                    float value = (float) scaled;
                    if (!Double.isFinite(scaled) || !Float.isFinite(value) || value < 0f)
                        return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);
                    damage.put(amount.getKey(), value);
                }
                damageByEntity.put(entry.getKey(), damage);
            }
        } catch (RuntimeException exception) { return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0); }
        for (var entry : observed.entrySet()) if (!Objects.equals(entry.getValue(), stateIfLoaded(level, entry.getKey())))
            return new Result(Status.INVALID_OR_STALE, plan.visited(), 0, 0, 0);

        if (data.temperatureKelvin().isPresent()) {
            for (var entry : plan.intensities().entrySet()) {
                if (!(entry.getValue() > 0)) continue;
                BlockPos pos = new BlockPos(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
                AtmosphereService.INSTANCE.exposeExplosionHotspot(level, pos, data.temperatureKelvin().get(), entry.getValue());
            }
        }

        int damaged = 0;
        var orderedHits = new ArrayList<>(hits.entrySet());
        orderedHits.sort(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)));
        boolean failed = false;
        // Apply one outward impulse per frozen entity hit, before damage can alter entity state.
        for (var entry : orderedHits) {
            LivingEntity entity = entry.getValue().entity;
            if (!eligibleForKnockback(entity)) continue;
            try {
                var impulse = BlastKnockbackPolicy.calculate(entry.getValue().intensity,
                        source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
                if (!impulse.applicable()) continue;
                var existing = entity.getDeltaMovement();
                var velocity = impulse.velocity();
                double x = (Double.isFinite(existing.x) ? existing.x : 0) + velocity.x();
                double y = (Double.isFinite(existing.y) ? existing.y : 0) + velocity.y();
                double z = (Double.isFinite(existing.z) ? existing.z : 0) + velocity.z();
                double scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
                if (!Double.isFinite(scale)) { x = velocity.x(); y = velocity.y(); z = velocity.z(); scale = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)); }
                double length = scale * Math.sqrt((x / scale) * (x / scale) + (y / scale) * (y / scale) + (z / scale) * (z / scale));
                if (length > BlastKnockbackPolicy.MAX_SPEED) {
                    double factor = BlastKnockbackPolicy.MAX_SPEED / length;
                    x *= factor; y *= factor; z *= factor;
                }
                entity.setDeltaMovement(x, y, z);
                entity.hasImpulse = true;
                entity.hurtMarked = true;
            } catch (RuntimeException ignored) { failed = true; }
        }
        for (var entry : orderedHits) {
            try {
                // APPLIED means the ledger accepted the request; vanilla hurt cancellation may still reject projected health damage.
                DamageSystem.Result applied = DamageSystem.applyHealthChange(entry.getValue().entity, damageByEntity.get(entry.getKey()), 1f, false, DamageSystem.Cause.EXPLOSION);
                if (applied == DamageSystem.Result.APPLIED) damaged++; else failed = true;
            } catch (RuntimeException ignored) { failed = true; }
        }

        int mutated = 0;
        for (BlastPlanner.Pos cell : plan.breached()) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (stateIfLoaded(level, pos) == null || !Objects.equals(observed.get(pos), stateIfLoaded(level, pos))) {
                failed = true; continue;
            }
            try {
                var old = level.getBlockState(pos);
                boolean changed = level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                if (!changed || !level.getBlockState(pos).isAir() || old.equals(level.getBlockState(pos))) { failed = true; continue; }
                mutated++;
            } catch (RuntimeException ignored) { failed = true; continue; }
            try {
                CableStorage.removeHost(level, pos);
            } catch (RuntimeException ignored) { failed = true; }
            try {
                PowerGraphService.noteChanged(level, new ChunkPos(pos));
            } catch (RuntimeException ignored) { failed = true; }
            try {
                AtmosphereService.INSTANCE.topologyChanged(level, pos);
            } catch (RuntimeException ignored) { failed = true; }
        }
        return new Result(failed ? Status.PARTIAL : Status.APPLIED, plan.visited(), plan.breached().size(), mutated, damaged);
    }

    /**
     * Finite-tolerance policy for ordinary destructible blocks. Negative hardness denotes
     * vanilla-unbreakable/protected blocks. All other non-air states, including block entities
     * and non-colliding decorations, are breached by the existing setBlock-to-air primitive.
     * That primitive does not preserve container contents; callers must account for that risk.
     */
    static BlastPlanner.Sample classify(net.minecraft.world.level.block.state.BlockState state) {
        return classify(state, net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static BlastPlanner.Sample classify(net.minecraft.world.level.block.state.BlockState state,
                                                  net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        if (isExplicitlyProtected(state) || state.getDestroySpeed(level, pos) < 0)
            return BlastPlanner.Sample.solid(Double.POSITIVE_INFINITY);
        if (state.isAir()) return BlastPlanner.Sample.open();
        return BlastPlanner.Sample.solid(FIXTURE_TOLERANCE);
    }

    private static boolean isExplicitlyProtected(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.JIGSAW)
                || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(Blocks.LIGHT)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.NETHER_PORTAL);
    }

    private static BlastPlanner.Sample sample(ServerLevel level, BlockPos pos, Map<BlockPos, net.minecraft.world.level.block.state.BlockState> observed) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            observed.putIfAbsent(pos.immutable(), null);
            return BlastPlanner.Sample.unknown();
        }
        var state = stateIfLoaded(level, pos);
        observed.putIfAbsent(pos.immutable(), state);
        return state == null ? BlastPlanner.Sample.unknown() : classify(state, level, pos);
    }
    private static net.minecraft.world.level.block.state.BlockState stateIfLoaded(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return null;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }
    private static boolean intersects(AABB box, BlockPos pos) { return box.intersects(new AABB(pos)); }
    private static boolean eligibleForKnockback(LivingEntity entity) {
        return eligibleForPhysicalHit(entity) && !entity.noPhysics
                && (!(entity instanceof Player player) || !player.isSpectator());
    }
    private static boolean eligibleForPhysicalHit(LivingEntity entity) {
        return entity != null && !entity.isRemoved() && (entity.isAlive()
                || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isRetainedNonAliveBody(entity));
    }
    private static Result result(Status status, int visited) { return new Result(status, visited, 0, 0, 0); }
    private record EntityHit(LivingEntity entity, double intensity) { }
}

