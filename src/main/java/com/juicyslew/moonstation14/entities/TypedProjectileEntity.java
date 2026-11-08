package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility;
import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import com.juicyslew.moonstation14.ms14.combat.ProjectileImpactPolicy;
import com.juicyslew.moonstation14.ms14.combat.ProjectilePresentationMotion;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.core.BlockPos;
import java.util.Objects;

/** Server-authoritative, profile-frozen generic typed projectile. */
public class TypedProjectileEntity extends Projectile {
    private static final EntityDataAccessor<String> VISUAL = SynchedEntityData.defineId(TypedProjectileEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FLY_BY = SynchedEntityData.defineId(TypedProjectileEntity.class, EntityDataSerializers.STRING);
    @FunctionalInterface public interface FlyByPresentationCallback {
        void present(TypedProjectileEntity projectile, Vec3 previous, Vec3 current, ProjectileProfile.FlyByData descriptor);
    }
    private static volatile FlyByPresentationCallback flyByPresentation = (entity, previous, current, descriptor) -> {};
    private boolean wasInsideFlyByRange;
    private String profileId;
    private ProjectileProfile profile;
    private int age;
    private boolean impactPublished;

    public TypedProjectileEntity(EntityType<? extends TypedProjectileEntity> type, Level level) { super(type, level); }

    /** Use the entity's configured tracking range for presentation, not its tiny collision box. */
    @Override public boolean shouldRenderAtSqrDistance(double distance) {
        return withinTrackingRenderDistance(distance, getType().clientTrackingRange(), Entity.getViewScale());
    }

    /** Local presentation adaptation: use tracking range rather than projectile collision bounds. */
    public static boolean withinTrackingRenderDistance(double distanceSquared, int trackingChunks, double viewScale) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0 || trackingChunks < 0
                || !Double.isFinite(viewScale) || viewScale <= 0.0) return false;
        double range = trackingChunks * 16.0 * viewScale;
        return distanceSquared < range * range;
    }

    public static TypedProjectileEntity create(ServerLevel level, String prototypeId, ProjectileProfile profile,
                                                LivingEntity shooter, Vec3 position, Vec3 velocity) {
        Objects.requireNonNull(level); Objects.requireNonNull(profile); Objects.requireNonNull(shooter);
        Objects.requireNonNull(position); Objects.requireNonNull(velocity);
        if (!finite(position) || !finite(velocity)) throw new IllegalArgumentException("projectile position and velocity must be finite");
        if (!profile.deleteOnCollide()) throw new IllegalArgumentException("delete_on_collide=false is unsupported");
        var entity = new TypedProjectileEntity(ModEntities.TYPED_PROJECTILE.get(), level);
        entity.profileId = Objects.requireNonNull(prototypeId);
        entity.profile = profile;
        entity.entityData.set(VISUAL, encodeVisual(profile));
        entity.entityData.set(FLY_BY, encodeFlyBy(profile));
        entity.setOwner(shooter);
        entity.setPos(position);
        entity.setDeltaMovement(velocity);
        return entity;
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 previous=position();
            Vec3 current=ProjectilePresentationMotion.advance(previous, getDeltaMovement());
            setPos(current);
            ProjectileProfile.FlyByData descriptor=flyBy();
            if(descriptor!=null) flyByPresentation.present(this,previous,current,descriptor);
            return;
        }
        Vec3 motion = getDeltaMovement();
        if (!(level() instanceof ServerLevel server) || ProjectileImpactPolicy.discardForMissingProfile(ProjectileImpactPolicy.TickSide.SERVER, profile != null) || !finite(motion)) { discard(); return; }
        if (++age >= ProjectileImpactPolicy.lifetimeTicks(profile)) { discard(); return; }
        Vec3 delta = getDeltaMovement();
        if (!server.hasChunkAt(BlockPos.containing(position())) || !server.hasChunkAt(BlockPos.containing(position().add(delta)))) return;
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, candidate -> (!profile.ignoreShooter() || candidate != getOwner())
                && candidate.level() == level() && CommonMeleeTargetEligibility.damageable(candidate));
        if (hit.getType() == HitResult.Type.MISS) { setPos(position().add(delta)); return; }
        publishImpact(server,hit,motion);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living)
            com.juicyslew.moonstation14.ms14.combat.GunImpactEffects.apply(living, profile, getOwner() instanceof LivingEntity owner ? owner : null);
        else if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LooseItemEntity loose)
            loose.applyTypedItemDamage(server, profile.damage(), profile.ignoreResistances());
        if (ProjectileImpactPolicy.consumeCollision(profile)) discard();
    }

    private void publishImpact(ServerLevel server,HitResult hit,Vec3 motion){
        if(!ProjectileImpactPolicy.emitImpact(profile,impactPublished))return; impactPublished=true;
        var owner=getOwner() instanceof LivingEntity living?living:null;
        if(profile.hitSound()!=null){var cue=profile.hitSound();com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter.play(server,cue.event(),net.minecraft.sounds.SoundSource.PLAYERS,hit.getLocation().x,hit.getLocation().y,hit.getLocation().z,cue.volume(),com.juicyslew.moonstation14.ms14.combat.MeleeSoundPolicy.pitch(1f,server.getRandom().nextFloat(),cue.pitchVariation()));}
        com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashObserverPublisher.publishImpact(owner,this,profile.impactEffect(),hit.getLocation(),motion);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static String encodeVisual(ProjectileProfile profile) {
        return com.juicyslew.moonstation14.ms14.combat.ProjectileVisualSyncCodec.encode(profile.sprite());
    }
    public ProjectileProfile.SpriteData visual() {
        return com.juicyslew.moonstation14.ms14.combat.ProjectileVisualSyncCodec.decode(entityData.get(VISUAL));
    }
    private static String encodeFlyBy(ProjectileProfile profile) {
        if(profile.flyBySound()==null)return "";
        return ProjectileProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow().toString();
    }
    public ProjectileProfile.FlyByData flyBy() {
        try { return ProjectileProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                net.minecraft.util.GsonHelper.parse(entityData.get(FLY_BY))).getOrThrow().flyBySound(); }
        catch(RuntimeException e){return null;}
    }
    public boolean wasInsideFlyByRange(){return wasInsideFlyByRange;}
    public void setWasInsideFlyByRange(boolean value){wasInsideFlyByRange=value;}
    public static void installFlyByPresentationCallback(FlyByPresentationCallback callback){flyByPresentation=Objects.requireNonNull(callback);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(VISUAL, ""); builder.define(FLY_BY, ""); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (profile != null) { tag.putString("profile_id", profileId); tag.putInt("age", age); tag.put("frozen_profile", ProjectileProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).getOrThrow()); }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            profileId = tag.getString("profile_id"); age = tag.getInt("age");
            profile = tag.contains("frozen_profile") ? ProjectileProfile.CODEC.parse(NbtOps.INSTANCE, tag.get("frozen_profile")).getOrThrow() : null;
            if (profile == null || !profile.deleteOnCollide()) discard();
        } catch (RuntimeException exception) { discard(); }
    }
}
