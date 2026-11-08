package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.entities.TypedProjectileEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/** Plans and publishes a single moving-projectile shot (including finite pellet fan-out). */
public final class GunShotEmitter {
    private GunShotEmitter() { }

    /** Resolves and validates the whole shot before any projectile is constructed or published. */
    public static Optional<PreparedShot> prepare(ServerLevel level, LivingEntity shooter, ResourceLocation shotId,
                                                  Vec3 origin, Vec3 direction, Vec3 shooterVelocity,
                                                  float projectileSpeed, float spreadMultiplier) {
        if (level == null || shooter == null || shooter.level() != level || shotId == null
                || !finite(origin) || !finite(direction) || direction.lengthSqr() < 1.0e-12
                || !finite(shooterVelocity) || !Float.isFinite(projectileSpeed) || projectileSpeed <= 0
                || !Float.isFinite(spreadMultiplier) || spreadMultiplier < 0) return Optional.empty();
        var catalog = ModProjectileProfiles.serverCatalog();
        Optional<List<PlannedProjectile>> planned = plan(catalog.asMap(), shotId, direction, shooterVelocity, projectileSpeed, spreadMultiplier);
        if (planned.isEmpty()) return Optional.empty();
        List<PlannedProjectile> plan = new ArrayList<>(planned.get().size());
        for (PlannedProjectile projectile : planned.get()) {
            ProjectileProfile profile = projectile.profile();
            Vec3 ray = projectile.direction();
            Vec3 velocity = projectile.velocity();
            ResourceLocation id = projectile.id();
            if (profile.hitscan() != null) {
                double range = profile.hitscan().range();
                Vec3 endpoint = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe
                        .loadedRayEndpoint(level, shooter, origin, ray, range, true);
                if (endpoint == null) return Optional.empty();
                TargetHit hit = nearestTarget(level, shooter, origin, endpoint, range, profile.ignoreShooter());
                Vec3 actualEnd=hit==null?endpoint:hit.point();
                plan.add(new PlannedProjectile(id, profile, velocity, ray, actualEnd, hit==null?null:hit.entity()));
            } else plan.add(projectile);
        }
        return Optional.of(new PreparedShot(level, shooter, origin, List.copyOf(plan)));
    }

    /** Pure catalog/math planning; child spread profiles never recursively fan out. */
    public static Optional<List<PlannedProjectile>> plan(Map<ResourceLocation, ProjectileProfile> catalog,
            ResourceLocation shotId, Vec3 direction, Vec3 shooterVelocity, float speed, float multiplier) {
        if (catalog == null || shotId == null || !finite(direction) || direction.lengthSqr() < 1.0e-12
                || !finite(shooterVelocity) || !Float.isFinite(speed) || speed <= 0
                || !Float.isFinite(multiplier) || multiplier < 0) return Optional.empty();
        ProjectileProfile original = catalog.get(shotId);
        if (original == null) return Optional.empty();
        ProjectileProfile.SpreadData pattern = original.spread();
        int total = pattern == null ? 1 : pattern.count();
        double extent = pattern == null ? 0 : Math.toRadians(pattern.degrees() * multiplier);
        if (total < 1 || !Double.isFinite(extent) || extent > Math.PI) return Optional.empty();
        ResourceLocation childId = pattern == null ? null : pattern.childProjectile().orElse(null);
        ProjectileProfile child = childId == null ? original : catalog.get(childId);
        if (child == null) return Optional.empty();
        List<PlannedProjectile> result = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Vec3 ray;
            try { ray = RangedDirection.coneSample(direction, extent / 2, i, total); }
            catch (IllegalArgumentException invalid) { return Optional.empty(); }
            Vec3 velocity = ray.scale(speed / 20d).add(shooterVelocity);
            if (!finite(velocity)) return Optional.empty();
            boolean root = i == 0 || childId == null;
            result.add(new PlannedProjectile(root ? shotId : childId, root ? original : child, velocity, ray, null, null));
        }
        return Optional.of(List.copyOf(result));
    }

    public record PreparedShot(ServerLevel level, LivingEntity shooter, Vec3 origin, List<PlannedProjectile> projectiles) {
        public PreparedShot { projectiles = List.copyOf(projectiles); }
        /** Called only after the owning firing transaction has committed ammunition. */
        public boolean publish() {
            return publish(null);
        }
        public boolean publish(ShotVisualSource source) {
            List<TypedProjectileEntity> staged = new ArrayList<>(projectiles.size());
            try {
                for (PlannedProjectile p : projectiles)
                    if (p.endpoint() == null) staged.add(TypedProjectileEntity.create(level, p.id().toString(), p.profile(), shooter, origin, p.velocity()));
                    else {
                        boolean entityHit=p.target()!=null&&live(level,p.target())
                                && (p.profile().ignoreShooter() || p.target()!=shooter)
                                && damageableOpaque(p.target());
                        if(entityHit&&p.target() instanceof LivingEntity living) GunImpactEffects.apply(living,p.profile(),shooter);
                        else if(entityHit&&p.target() instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose) loose.applyTypedItemDamage(level,p.profile().damage(),p.profile().ignoreResistances());
                        boolean blockHit=origin.distanceTo(p.endpoint())<p.profile().hitscan().range()-.01;
                        if(p.profile().travelEffect()!=null)GunMuzzleFlashObserverPublisher.publishBeam(shooter,origin,p.endpoint(),p.profile().travelEffect(),source);
                        if((entityHit||blockHit)&&p.profile().impactEffect()!=null)GunMuzzleFlashObserverPublisher.publishImpact(shooter,level,p.profile().impactEffect(),p.endpoint(),p.direction());
                    }
                for (TypedProjectileEntity entity : staged)
                    if (!level.addFreshEntity(entity)) { staged.forEach(TypedProjectileEntity::discard); return false; }
                return true;
            } catch (RuntimeException failure) {
                staged.forEach(TypedProjectileEntity::discard);
                return false;
            }
        }
    }
    public record PlannedProjectile(ResourceLocation id, ProjectileProfile profile, Vec3 velocity, Vec3 direction,
                                    Vec3 endpoint, Entity target) {
        public PlannedProjectile(ResourceLocation id, ProjectileProfile profile, Vec3 velocity) { this(id, profile, velocity, velocity.normalize(), null, null); }
    }
    public record TargetHit(Entity entity,Vec3 point) { }
    public static TargetHit nearestTarget(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 to, double range, boolean ignoreShooter) {
        Entity best = null; Vec3 bestPoint=null; double nearest = range * range;
        for (Entity candidate : level.getEntities((Entity) null, new AABB(from, to).inflate(.5), e ->
                damageableOpaque(e) && (ignoreShooter || e != shooter))) {
            if (!live(level, candidate) || (ignoreShooter && candidate == shooter)) continue;
            AABB bounds = candidate.getBoundingBox().inflate(.05);
            Vec3 hit = bounds.contains(from) ? from : bounds.clip(from, to).orElse(null);
            if (hit == null) continue;
            double distance = from.distanceToSqr(hit);
            if (distance < nearest) { nearest = distance; best = candidate; bestPoint=hit; }
        }
        return best==null?null:new TargetHit(best,bestPoint);
    }
    /** SS14 MobLayer maps to opaque bodies; item-layer opacity is not modeled, so loose items do not stop rays. */
    private static boolean damageableOpaque(Entity entity) {
        return entity instanceof LivingEntity && CommonMeleeTargetEligibility.damageable(entity);
    }
    private static boolean live(ServerLevel level, Entity entity) {
        return entity != null && entity.level() == level && !entity.isRemoved() && entity.isAlive()
                && entity.isAddedToLevel() && level.getEntity(entity.getUUID()) == entity;
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
