package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData;
import com.juicyslew.moonstation14.ms14.construction.prototype.RadiationBlockerProfile;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Loaded-world, server-authoritative radiation dose runtime. */
public final class RadiationService {
    public static final RadiationService INSTANCE = new RadiationService();
    private final Map<ServerLevel, Map<UUID, Source>> sources = new IdentityHashMap<>();
    private final Map<ServerLevel, Map<UUID, DoseReceiver>> receivers = new IdentityHashMap<>();
    private final Map<ServerLevel, RadiationIntervalClock> clocks = new IdentityHashMap<>();
    private RadiationService() { }

    public synchronized boolean register(ServerLevel level, UUID id, BlockPos pos, RadiationSourceProfile profile) {
        if (level == null || level.isClientSide() || id == null || pos == null || profile == null || !loaded(level,pos)) return false;
        Map<UUID,Source> map = sources.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        map.put(id, new Source(pos.immutable(), profile)); return true;
    }
    /** Registers a moving source at its actual 3-D position; chunk eligibility remains loaded-only. */
    public synchronized boolean register(ServerLevel level, UUID id, Vec3 pos, RadiationSourceProfile profile) {
        if (level == null || level.isClientSide() || id == null || pos == null || profile == null
                || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || !level.hasChunkAt(BlockPos.containing(pos))) return false;
        sources.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(id, new Source(pos, profile));
        return true;
    }
    public synchronized void remove(ServerLevel level, UUID id) {
        Map<UUID,Source> map = sources.get(level); if (map == null) return;
        map.remove(id); if (map.isEmpty()) sources.remove(level);
    }
    /** Registers a loaded block-entity/actor receiver; the callback receives rads/sec and exposure seconds. */
    public synchronized boolean registerReceiver(ServerLevel level, UUID id, BlockPos pos, RadiationDoseReceiver receiver) {
        if (level == null || level.isClientSide() || id == null || pos == null || receiver == null || !loaded(level, pos)) return false;
        receivers.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(id, new DoseReceiver(pos.immutable(), receiver));
        return true;
    }
    public synchronized void removeReceiver(ServerLevel level, UUID id) {
        Map<UUID,DoseReceiver> map = receivers.get(level); if (map == null) return;
        map.remove(id); if (map.isEmpty()) receivers.remove(level);
    }
    public synchronized void onChunkUnload(ServerLevel level, ChunkPos chunk) {
        Map<UUID,Source> map = sources.get(level);
        if (map != null) { map.values().removeIf(s -> new ChunkPos(BlockPos.containing(s.position)).equals(chunk)); if (map.isEmpty()) sources.remove(level); }
        Map<UUID,DoseReceiver> registered = receivers.get(level);
        if (registered != null) { registered.values().removeIf(r -> new ChunkPos(r.pos).equals(chunk)); if (registered.isEmpty()) receivers.remove(level); }
    }
    public synchronized void clear(ServerLevel level) { sources.remove(level); receivers.remove(level); clocks.remove(level); }

    /** Called each post-server-tick; integrates only elapsed one-second intervals. */
    public synchronized void tick(ServerLevel level, long gameTime) {
        int elapsedTicks = clocks.computeIfAbsent(level, ignored -> new RadiationIntervalClock()).advance(gameTime);
        if (elapsedTicks == 0) return;
        Map<UUID,Source> map = sources.get(level); if (map == null || map.isEmpty()) return;
        map.values().removeIf(s -> !loaded(level, BlockPos.containing(s.position)));
        if (map.isEmpty()) return;
        Map<net.minecraft.world.level.block.Block, RadiationBlockerProfile> blockers = resolvedBlockers();
        Map<UUID, Hit> hits = new LinkedHashMap<>();
        for (Source source : map.values()) {
            double range = source.profile.range();
            AABB area = new AABB(BlockPos.containing(source.position)).inflate(range + 1);
            ArrayList<LivingEntity> candidates = new ArrayList<>();
            level.getEntities(EntityTypeTest.forClass(LivingEntity.class), area, LivingEntity::isAlive,
                    candidates, Integer.MAX_VALUE);
            for (LivingEntity entity : candidates) {
                if (!eligible(entity)) continue;
                double distance = entity.position().distanceTo(source.position);
                double rads = source.profile.atDistance(distance);
                double resistance = pathResistance(level, BlockPos.containing(source.position), entity.position(), blockers);
                if (!Double.isFinite(resistance)) continue;
                rads = Math.max(0, rads - resistance);
                hits.merge(entity.getUUID(), new Hit(entity, rads), (a,b) -> new Hit(a.entity, a.rads+b.rads));
            }
        }
        float elapsed = elapsedTicks / 20f;
        for (Hit hit : hits.values()) if (hit.rads >= .1) DamageSystem.applyHealthChange(hit.entity,
                Map.of(DamageKeys.RADIATION, (float)(hit.rads * elapsed)), 1f, false, DamageSystem.Cause.RADIATION);
        Map<UUID,DoseReceiver> registered = receivers.get(level);
        if (registered != null) {
            registered.values().removeIf(r -> !loaded(level, r.pos));
            for (DoseReceiver receiver : registered.values()) {
                double total = 0;
                for (Source source : map.values()) {
                    double distance = source.position.distanceTo(receiver.pos.getCenter());
                    if (distance > source.profile.range()) continue;
                    double rads = source.profile.atDistance(distance) - pathResistance(level, BlockPos.containing(source.position), receiver.pos.getCenter(), blockers);
                    if (Double.isFinite(rads)) total += Math.max(0, rads);
                }
                if (total >= .1) receiver.callback.receive(total, elapsed);
            }
        }
    }
    private static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }
    private static boolean eligible(LivingEntity entity) {
        if (entity == null || entity.isRemoved() || !entity.isAlive() || entity.isDeadOrDying()) return false;
        return !(entity instanceof Player player && (player.isSpectator()
                || player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()) != null));
    }
    /** 3-D voxel DDA. Unloaded cells are unknown and suppress dose rather than force-loading. */
    private static double pathResistance(ServerLevel level, BlockPos source, net.minecraft.world.phys.Vec3 target,
                                         Map<net.minecraft.world.level.block.Block, RadiationBlockerProfile> blockers) {
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(source.getX()+.5, source.getY()+.5, source.getZ()+.5);
        double dx=target.x-start.x, dy=target.y-start.y, dz=target.z-start.z;
        int sx=Double.compare(dx,0), sy=Double.compare(dy,0), sz=Double.compare(dz,0);
        double tx=firstBoundary(start.x,dx,sx), ty=firstBoundary(start.y,dy,sy), tz=firstBoundary(start.z,dz,sz);
        double dtx=sx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx);
        double dty=sy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy);
        double dtz=sz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        int x=source.getX(), y=source.getY(), z=source.getZ();
        BlockPos end=BlockPos.containing(target); double resistance=0;
        for (int i=0;i<512;i++) {
            double t=Math.min(tx,Math.min(ty,tz));
            if (!Double.isFinite(t) || t>=1 || (x==end.getX()&&y==end.getY()&&z==end.getZ())) break;
            if (tx<=ty && tx<=tz) { x+=sx; tx+=dtx; }
            else if (ty<=tz) { y+=sy; ty+=dty; }
            else { z+=sz; tz+=dtz; }
            BlockPos pos=new BlockPos(x,y,z);
            if (!loaded(level,pos)) return Double.POSITIVE_INFINITY;
            var state = level.getBlockState(pos);
            // Only blockers explicitly present in a resolved local entity prototype shield. Vanilla and
            // unmapped local blocks intentionally have no inferred resistance.
            RadiationBlockerProfile blocker = blockers.get(state.getBlock());
            if (blocker != null) resistance += blocker.resistance();
        }
        return resistance;
    }
    private static Map<net.minecraft.world.level.block.Block, RadiationBlockerProfile> resolvedBlockers() {
        Map<net.minecraft.world.level.block.Block, RadiationBlockerProfile> result = new IdentityHashMap<>();
        for (ConstructionEntityData prototype : PrototypeRuntime.serverConstructionEntities().values()) {
            if (prototype.radiationBlocker().isEmpty()) continue;
            try {
                var blockId = net.minecraft.resources.ResourceLocation.parse(prototype.block());
                if (BuiltInRegistries.BLOCK.containsKey(blockId))
                    result.put(BuiltInRegistries.BLOCK.get(blockId), prototype.radiationBlocker().orElseThrow());
            } catch (RuntimeException ignored) { /* Invalid/unknown block mappings cannot shield. */ }
        }
        return result;
    }
    private static double firstBoundary(double origin, double delta, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary=step>0?Math.floor(origin)+1:Math.floor(origin);
        return (boundary-origin)/delta;
    }
    private record Source(Vec3 position, RadiationSourceProfile profile) {
        private Source(BlockPos pos, RadiationSourceProfile profile) { this(pos.getCenter(), profile); }
    }
    private record DoseReceiver(BlockPos pos, RadiationDoseReceiver callback) { }
    private record Hit(LivingEntity entity, double rads) { }
}

