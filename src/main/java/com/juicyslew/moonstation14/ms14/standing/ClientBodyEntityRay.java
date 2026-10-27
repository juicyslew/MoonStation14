package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Shared client eye/look ray for medical body nominations and combat candidates. */
public final class ClientBodyEntityRay {
    private ClientBodyEntityRay() { }

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

    /** Returns eligible hits along each ray in ray order, deduplicated in first-hit order. */
    public static List<UUID> fan(Entity attacker, double rayLength, double widthRadians, int maxTargets,
                                 Predicate<Entity> eligible, Function<Entity, AABB> bounds) {
        if (attacker == null || attacker.level() != net.minecraft.client.Minecraft.getInstance().level
                || !Double.isFinite(rayLength) || rayLength <= 0 || !Double.isFinite(widthRadians)
                || widthRadians < 0 || maxTargets <= 0) return List.of();
        Vec3 start=attacker.getEyePosition(); Vec3 look=attacker.getLookAngle();
        double aim=Math.atan2(-look.x, look.z);
        List<Double> angles=fanAngles(aim,widthRadians);
        var candidates=attacker.level().getEntities(attacker, attacker.getBoundingBox().inflate(rayLength+1), entity -> entity!=attacker && !entity.isRemoved() && entity.level()==attacker.level());
        var rayHits=new java.util.ArrayList<Collection<UUID>>();
        for(double angle:angles) {
            Vec3 direction=new Vec3(-Math.sin(angle),look.y,Math.cos(angle));
            if(direction.lengthSqr()==0) continue;
            Vec3 end=start.add(direction.normalize().scale(rayLength));
            var block=attacker.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,attacker));
            double blocker=block.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?start.distanceTo(block.getLocation()):rayLength;
            var hits=new LinkedHashSet<UUID>();
            for(var entity:candidates) if(eligible.test(entity) && BodyInteractionRay.hit(bounds.apply(entity),start,end,rayLength,blocker).isPresent()) hits.add(entity.getUUID());
            rayHits.add(hits);
        }
        return mergeFanHits(rayHits,maxTargets);
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
