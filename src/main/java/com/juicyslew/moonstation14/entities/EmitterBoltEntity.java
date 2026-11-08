package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.singularity.emitter.EmitterProfile;
import com.juicyslew.moonstation14.ms14.singularity.emitter.ModEmitters;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByProfile;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyBySource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Server-authoritative beam bolt. Its loaded-space sweep is bounded to the two-block movement interval. */
public final class EmitterBoltEntity extends Projectile implements ProjectileFlyBySource {
    private static final String AGE="BoltAge", DAMAGE="HeatDamage", LIFE="Lifetime", HALF="HalfExtent", IMPACT="ImpactSound", IMPACT_LIFE="ImpactLifetime", PROFILE="EmitterProfile";
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> SYNC_PROFILE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EmitterBoltEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> SYNC_SOURCE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EmitterBoltEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private int age;
    private int lifetime = 60;
    private float heatDamage = 14;
    private double halfExtent = .2;
    private java.util.UUID emitterIdentity;
    private net.minecraft.resources.ResourceLocation emitterProfileId;
    private net.minecraft.resources.ResourceLocation impactSound = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "pa_particle_hit");
    private int impactLifetimeTicks = 5;

    public EmitterBoltEntity(EntityType<? extends EmitterBoltEntity> type, Level level) { super(type, level); }
    public EmitterBoltEntity(Level level, EmitterBlockEntity emitter, EmitterProfile profile) {
        this(ModEntities.EMITTER_BOLT.get(), level);
        emitterIdentity = emitter.placementIdentity();
        emitterProfileId = emitter.profileId();
        entityData.set(SYNC_PROFILE, emitterProfileId.toString());
        entityData.set(SYNC_SOURCE, emitterIdentity.toString());
        lifetime = profile.boltLifetimeTicks(); heatDamage = profile.boltHeatDamage(); halfExtent = profile.boltHalfExtent();
        impactSound = profile.boltImpactSound();
        impactLifetimeTicks = profile.boltImpactLifetimeTicks();
    }
    /** Placement identity of the emitter that created this bolt, or null for an unowned bolt. */
    public java.util.UUID emitterIdentity() { return emitterIdentity; }
    @Override public java.util.UUID flyByShooterIdentity() {
        String id = entityData.get(SYNC_SOURCE);
        if (id != null && !id.isEmpty()) try { return java.util.UUID.fromString(id); }
        catch (IllegalArgumentException ignored) { }
        return emitterIdentity;
    }
    @Override public Vec3 flyByPosition() { return position(); }
    @Override public ProjectileFlyByProfile flyByProfile() {
        String id = entityData.get(SYNC_PROFILE);
        var profileId = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (profileId == null && emitterProfileId != null) profileId = emitterProfileId;
        EmitterProfile profile = profileId == null || level() == null ? null : ModEmitters.catalog(level()).get(profileId);
        return profile == null ? null : profile.boltFlyBy();
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            // Bare Projectile tick does no native movement; spawn/correction packets
            // rebase this prediction before this single velocity step.
            setPos(EmitterBoltClientTrajectory.advance(position(), getDeltaMovement(), isRemoved()));
            return;
        }
        if (++age >= lifetime) { discard(); return; }
        Vec3 from = position(), to = from.add(getDeltaMovement());
        if (!ProjectileLoadedSweep.isLoadedSpan(level, from, to, halfExtent)) return;
        AABB swept = getBoundingBox().expandTowards(getDeltaMovement());
        HitResult blockHit = ProjectileLoadedSweep.clip(level,this,from,to,halfExtent);
        if(blockHit==null) return;
        Entity target = level.getEntities(this, swept, e -> e instanceof LivingEntity && e.isAlive()
                && (emitterIdentity == null || !emitterIdentity.equals(e.getUUID()))) .stream()
                .filter(e -> e.getBoundingBox().inflate(halfExtent).clip(from,to).isPresent())
                .min(java.util.Comparator.comparingDouble(e -> from.distanceToSqr(e.getBoundingBox().inflate(halfExtent).clip(from,to).orElse(from))))
                .orElse(null);
        if (target != null && (blockHit.getType()!=HitResult.Type.BLOCK
                || from.distanceToSqr(blockHit.getLocation()) >= from.distanceToSqr(target.position()))) {
            if(target instanceof LivingEntity living && EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.LIVING, living.isRemoved(), false)) {
                emitImpact(level, living.position().x, living.position().y, living.position().z);
                DamageSystem.applyHealthChange(living, Map.of(DamageKeys.HEAT, heatDamage), 1, false, DamageSystem.Cause.FIRE);
            }
            discard(); return;
        }
        if (blockHit.getType()==HitResult.Type.BLOCK) {
            // The ray location may lie on the outside face of a machine's owning cell.
            // BlockHitResult retains the exact cell whose collider was hit.
            BlockPos hit = ((net.minecraft.world.phys.BlockHitResult)blockHit).getBlockPos();
            if(level.getBlockEntity(hit) instanceof FieldGeneratorBlockEntity generator) {
                // ContainmentFieldGeneratorSystem.HandleGeneratorCollide receives charge without consuming the bolt;
                // its fixture inherits Fixture.Hard=true (Containment.yml fix1/BaseStructure omit an override).
                generator.receiveEmitterBolt();
                if (EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.FIELD_GENERATOR, false, true)) emitImpact(level, blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z);
            } else {
                boolean collision = !level.getBlockState(hit).getCollisionShape(level, hit).isEmpty();
                if (EmitterBoltImpactPolicy.emits(EmitterBoltImpactPolicy.Contact.BLOCK, false, collision)) emitImpact(level, blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z);
            }
            discard(); return;
        }
        setPos(to.x,to.y,to.z);
    }
    private void emitImpact(ServerLevel level, double x, double y, double z) {
        level.sendParticles(new EmitterImpactParticleOptions(impactLifetimeTicks), x, y, z, 1, 0, 0, 0, 0);
        com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(level, impactSound, x, y, z);
    }
    @Override protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        builder.define(SYNC_PROFILE, "");
        builder.define(SYNC_SOURCE, "");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(AGE,age); tag.putInt(LIFE,lifetime); tag.putFloat(DAMAGE,heatDamage); tag.putDouble(HALF,halfExtent);
        if(emitterIdentity!=null)tag.putUUID("EmitterIdentity",emitterIdentity);
        tag.putString(IMPACT, impactSound.toString()); tag.putInt(IMPACT_LIFE, impactLifetimeTicks);
        if(emitterProfileId!=null)tag.putString(PROFILE, emitterProfileId.toString());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        age=Math.max(0,tag.getInt(AGE)); lifetime=Math.max(1,tag.getInt(LIFE)); heatDamage=Math.max(0,tag.getFloat(DAMAGE));
        halfExtent=Math.max(.01,tag.getDouble(HALF)); if(tag.hasUUID("EmitterIdentity"))emitterIdentity=tag.getUUID("EmitterIdentity");
        var sound=net.minecraft.resources.ResourceLocation.tryParse(tag.getString(IMPACT)); if(sound!=null)impactSound=sound;
        impactLifetimeTicks=Math.max(1,Math.min(1200,tag.contains(IMPACT_LIFE)?tag.getInt(IMPACT_LIFE):5));
        emitterProfileId=net.minecraft.resources.ResourceLocation.tryParse(tag.getString(PROFILE));
        entityData.set(SYNC_PROFILE, emitterProfileId == null ? "" : emitterProfileId.toString());
        entityData.set(SYNC_SOURCE, emitterIdentity == null ? "" : emitterIdentity.toString());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
    @Override public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        lerpMotion(packet.getXa(), packet.getYa(), packet.getZa());
    }
}

