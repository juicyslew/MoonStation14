package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared client eye/look ray for medical body nominations and combat candidates. */
public final class ClientBodyEntityRay {
    private ClientBodyEntityRay() { }

    /** Eye-ray allowance for a physical reach measured from body bounds. */
    public static double eyeRayReach(double physicalRange, AABB bodyBounds) {
        return bodyBounds == null || !Double.isFinite(physicalRange) || physicalRange <= 0
                ? 0 : physicalRange + Math.sqrt(bodyBounds.getXsize() * bodyBounds.getXsize()
                        + bodyBounds.getYsize() * bodyBounds.getYsize() + bodyBounds.getZsize() * bodyBounds.getZsize());
    }

    /** Yaw-steered horizontal fan retaining the sampled look pitch. */
    public static Vec3 pitchedYawDirection(Vec3 look, double yaw) {
        return BodyInteractionRay.pitchedYawDirection(look, yaw);
    }

    public static UUID nearest(Entity attacker, double rayLength, Predicate<Entity> eligible,
                               Function<Entity, AABB> bounds) {
        if (attacker == null || attacker.level() != net.minecraft.client.Minecraft.getInstance().level
                || !Double.isFinite(rayLength) || rayLength <= 0) return null;
        Vec3 start = attacker.getEyePosition(), end = start.add(attacker.getLookAngle().scale(rayLength));
        var block = attacker.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, attacker));
        double blocker = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? start.distanceTo(block.getLocation()) : rayLength;
        var candidates = attacker.level().getEntities(attacker, new AABB(start, end).inflate(1.0), entity -> entity != attacker
                && !entity.isRemoved() && entity.level() == attacker.level());
        return selectEntities(candidates, start, end, rayLength, blocker, eligible, bounds);
    }

    /** First client-visible block contact on the normal eye ray (outline semantics, as body block use). */
    public static net.minecraft.world.phys.BlockHitResult firstBlockContact(Entity attacker, double rayLength) {
        if (attacker == null || attacker.level() != net.minecraft.client.Minecraft.getInstance().level
                || !Double.isFinite(rayLength) || rayLength <= 0) return null;
        Vec3 start = attacker.getEyePosition(), end = start.add(attacker.getLookAngle().scale(rayLength));
        var hit = attacker.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, attacker));
        return validBlockHit(hit);
    }

    private static net.minecraft.world.phys.BlockHitResult validBlockHit(net.minecraft.world.phys.HitResult hit) {
        return hit instanceof net.minecraft.world.phys.BlockHitResult block
                && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? block : null;
    }

    /** Shared semantic boundary: MISS results may be BlockHitResult instances but are never nominations. */
    public static com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block nominateStructuralContact(
            net.minecraft.world.phys.HitResult result, Vec3 start, double physicalRange,
            Function<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> states,
            Predicate<net.minecraft.world.level.block.state.BlockState> structural) {
        var hit = validBlockHit(result);
        if (hit == null || start == null || states == null || structural == null
                || start.distanceTo(hit.getLocation()) > physicalRange) return null;
        var state = states.apply(hit.getBlockPos());
        return state != null && structural.test(state)
                ? com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block.fromHit(hit, state) : null;
    }

    public record RayContact(UUID entity, net.minecraft.world.phys.BlockHitResult block, double distance) { }

    /** Select nearest physical contact; a blocking non-structural block still prevents entities behind it. */
    public static RayContact chooseLightContact(UUID entity, double entityDistance,
                                                net.minecraft.world.phys.BlockHitResult block, double blockDistance) {
        if (block != null && blockDistance < entityDistance) return new RayContact(null, block, blockDistance);
        if (entity != null && entityDistance < blockDistance) return new RayContact(entity, null, entityDistance);
        if (block != null && entity == null) return new RayContact(null, block, blockDistance);
        if (entity != null && block == null) return new RayContact(entity, null, entityDistance);
        return new RayContact(null, null, Double.POSITIVE_INFINITY);
    }

    /** Returns eligible hits along each ray in ray order, deduplicated in first-hit order. */
    public static List<UUID> fan(Entity attacker, double rayLength, double widthRadians, int maxTargets,
                                 Predicate<Entity> eligible, Function<Entity, AABB> bounds) {
        return fan(attacker, rayLength, widthRadians, maxTargets, eligible, bounds, null, Double.POSITIVE_INFINITY);
    }

    /** Typed wide-contact union. Structural blocks are nominatable; every collider remains an occluder. */
    public static List<com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination> fanContacts(
            Entity attacker, double rayLength, double widthRadians, int maxTargets,
            Predicate<Entity> eligible, Function<Entity, AABB> bounds, AABB physicalOrigin, double physicalRange,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> structural) {
        return fanContacts(attacker, attacker == null ? null : attacker.getEyePosition(), rayLength, widthRadians,
                maxTargets, eligible, bounds, physicalOrigin, physicalRange, structural);
    }
    public static List<com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination> fanContacts(
            Entity attacker, Vec3 start, double rayLength, double widthRadians, int maxTargets,
            Predicate<Entity> eligible, Function<Entity, AABB> bounds, AABB physicalOrigin, double physicalRange,
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> structural) {
        if (attacker == null || attacker.level() != net.minecraft.client.Minecraft.getInstance().level || !Double.isFinite(rayLength) || rayLength <= 0 || !Double.isFinite(widthRadians) || widthRadians < 0 || maxTargets <= 0 || (physicalOrigin != null && (!Double.isFinite(physicalRange) || physicalRange <= 0))) return List.of();
        Vec3 look=attacker.getLookAngle();
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z) || look.lengthSqr() == 0) return List.of();
        double aim=Math.toRadians(attacker.getYRot());
        var candidates=attacker.level().getEntities(attacker, attacker.getBoundingBox().inflate(rayLength+1), e -> e!=attacker&&!e.isRemoved());
        var union=new LinkedHashMap<Object,com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination>();
        for(var sample:fanSamples(start,look,aim,widthRadians,rayLength)) {
            Vec3 end=sample.end();
            var collider=attacker.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,attacker));
            double colliderBlocker=collider.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?start.distanceTo(collider.getLocation()):rayLength;
            var block=attacker.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,attacker));
            double outlineBlocker=block.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?start.distanceTo(block.getLocation()):rayLength;
            for(var e:entityHits(candidates,eligible,bounds,physicalOrigin,physicalRange,start,end,rayLength,Math.min(colliderBlocker,outlineBlocker)))
                union.putIfAbsent(e.getUUID(),new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Entity(e.getUUID()));
            var nomination=nominateStructuralContact(block, start, physicalRange, attacker.level()::getBlockState, structural);
            if(nomination != null) {
                var hit=(net.minecraft.world.phys.BlockHitResult) block;
                union.putIfAbsent(new BlockKey(hit.getBlockPos().asLong(),nomination.expectedBlockStateID()),nomination);
            }
            if(union.size()>=maxTargets) break;
        }
        return union.values().stream().limit(maxTargets).toList();
    }

    public static List<UUID> fan(Entity attacker, double rayLength, double widthRadians, int maxTargets,
                                 Predicate<Entity> eligible, Function<Entity, AABB> bounds,
                                 AABB physicalOrigin, double physicalRange) {
        if (attacker == null || attacker.level() != net.minecraft.client.Minecraft.getInstance().level
                || !Double.isFinite(rayLength) || rayLength <= 0 || !Double.isFinite(widthRadians)
                || widthRadians < 0 || maxTargets <= 0) return List.of();
        Vec3 start=attacker.getEyePosition(); Vec3 look=attacker.getLookAngle();
        if (!Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
                || look.lengthSqr() == 0) return List.of();
        double aim=Math.toRadians(attacker.getYRot());
        List<FanSample> samples=fanSamples(start,look,aim,widthRadians,rayLength);
        var candidates=attacker.level().getEntities(attacker, attacker.getBoundingBox().inflate(rayLength+1), entity -> entity!=attacker && !entity.isRemoved() && entity.level()==attacker.level());
        var rayHits=new java.util.ArrayList<Collection<UUID>>();
        for(var sample:samples) {
            Vec3 end=sample.end();
            var block=attacker.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,attacker));
            double blocker=block.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?start.distanceTo(block.getLocation()):rayLength;
            var hits=new LinkedHashSet<UUID>();
            for(var entity:entityHits(candidates,eligible,bounds,physicalOrigin,physicalRange,start,end,rayLength,blocker)) hits.add(entity.getUUID());
            rayHits.add(hits);
        }
        return mergeFanHits(rayHits,maxTargets);
    }

    /** Shared per-ray candidate filtering, physical reach, and collider-clipped body hit collection. */
    private static List<Entity> entityHits(Collection<? extends Entity> candidates, Predicate<Entity> eligible,
                                           Function<Entity,AABB> bounds, AABB physicalOrigin, double physicalRange,
                                           Vec3 start, Vec3 end, double rayLength, double blocker) {
        var hits = new java.util.LinkedHashMap<UUID,Entity>();
        for (var entity : candidates) {
            AABB entityBounds = bounds.apply(entity);
            if (eligible.test(entity)
                    && (physicalOrigin == null || com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.BodyBounds.withinRange(
                            com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.BodyBounds.distance(physicalOrigin, entityBounds), (float) physicalRange))
                    && BodyInteractionRay.hit(entityBounds,start,end,rayLength,blocker).isPresent())
                hits.putIfAbsent(entity.getUUID(), entity);
        }
        return List.copyOf(hits.values());
    }

    private record FanSample(Vec3 end) { }
    private record BlockKey(long position,int stateId) { }
    private static List<FanSample> fanSamples(Vec3 start,Vec3 look,double aim,double width,double length) {
        var samples=new java.util.ArrayList<FanSample>();
        for(double angle:fanAngles(aim,width)) { Vec3 direction=BodyInteractionRay.headLocalFanDirection(look,Math.toDegrees(aim),angle-aim); if(direction.lengthSqr()!=0) samples.add(new FanSample(start.add(direction.scale(length)))); }
        return samples;
    }

    /** SS14-style inclusive lower and exclusive upper fan sampling. */
    public static List<Double> fanAngles(double aimAngle,double widthRadians) {
        if(!Double.isFinite(aimAngle)||!Double.isFinite(widthRadians)||widthRadians<0) return List.of();
        int rays=1+35*(int)Math.ceil(widthRadians/(Math.PI*2));
        double step=widthRadians/rays;
        var result=new java.util.ArrayList<Double>(rays);
        for(int i=0;i<rays;i++) result.add(aimAngle-widthRadians/2+i*step);
        return List.copyOf(result);
    }
    /** Ordered hit union and nomination cap, shared by each live ray result. */
    public static List<UUID> mergeFanHits(Collection<? extends Collection<UUID>> rays,int maxTargets) {
        if(rays==null||maxTargets<=0)return List.of();
        var ids=new LinkedHashSet<UUID>();
        for(var ray:rays) if(ray!=null) ids.addAll(ray);
        return ids.stream().limit(maxTargets).toList();
    }

    /** Pure deterministic geometry selector; also used by the live world query above. */
    public static UUID selectEntities(Collection<? extends Entity> candidates, Vec3 start, Vec3 end,
                                       double rayLength, double blocker, Predicate<Entity> eligible,
                                       Function<Entity, AABB> bounds) {
        if (candidates == null || start == null || end == null || eligible == null || bounds == null) return null;
        return selectCandidate(candidates.stream().filter(eligible)
                .map(entity -> new Candidate(entity.getUUID(), bounds.apply(entity))).toList(),
                start, end, rayLength, blocker);
    }

    /** Pure geometry selector; live callers map the selected id back through their world query. */
    public static UUID selectCandidate(Collection<Candidate> candidates, Vec3 start, Vec3 end,
                                       double rayLength, double blocker) {
        if (candidates == null || start == null || end == null) return null;
        return candidates.stream()
                .filter(candidate -> BodyInteractionRay.hit(candidate.bounds(), start, end, rayLength, blocker).isPresent())
                .min(Comparator.<Candidate>comparingDouble(candidate -> BodyInteractionRay
                                .hit(candidate.bounds(), start, end, rayLength, blocker)
                                .map(start::distanceToSqr).orElse(Double.MAX_VALUE))
                        .thenComparing(candidate -> candidate.id().toString()))
                .map(Candidate::id).orElse(null);
    }

    public record Candidate(UUID id, AABB bounds) { }
}
