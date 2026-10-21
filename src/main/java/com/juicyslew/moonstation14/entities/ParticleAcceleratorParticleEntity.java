package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.singularity.pa.ModParticleAcceleratorParticles;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorParticleProfile;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrengthProfile;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyByProfile;
import com.juicyslew.moonstation14.ms14.projectile.ProjectileFlyBySource;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Native PA projectile: non-block-colliding radiation bolt with server-authoritative special consumers. */
public final class ParticleAcceleratorParticleEntity extends Projectile implements ProjectileFlyBySource {
    private static final EntityDataAccessor<String> PROFILE = SynchedEntityData.defineId(ParticleAcceleratorParticleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SOURCE_EMITTER_SYNC = SynchedEntityData.defineId(ParticleAcceleratorParticleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VISUAL_STRENGTH = SynchedEntityData.defineId(ParticleAcceleratorParticleEntity.class, EntityDataSerializers.INT);
    private static final String AGE="Age", SPENT="Spent", SOURCE="SourceEmitter", STRENGTH="Strength", POWER="PowerBoxMv", FOOD="SingularityFood", CHARGE="GeneratorCharge";
    private int age;
    private boolean spent;
    private UUID sourceEmitter;
    private ParticleAcceleratorStrengthProfile strength;
    private final Set<UUID> overlaps = new HashSet<>();
    private boolean diagnosticsEnabled;
    private ContactReceipt contactReceipt;
    public void enableCommissioningDiagnostics() { diagnosticsEnabled = true; }
    public ContactReceipt commissioningContactReceipt() { return contactReceipt; }
    public record ContactReceipt(long tick, String outcome, Vec3 position, UUID target, int foodRequested,
                                 double sourceEnergyBefore, double sourceEnergyAfter) { }
    private void recordContact(String outcome, Vec3 at, UUID target, int food, double before, double after) {
        if (diagnosticsEnabled) contactReceipt = new ContactReceipt(level().getGameTime(), outcome, at, target, food, before, after);
    }

    public ParticleAcceleratorParticleEntity(EntityType<? extends ParticleAcceleratorParticleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Strength must be the actual row resolved from the PA strength catalog, not a copied value table. */
    public ParticleAcceleratorParticleEntity(Level level, ResourceLocation profileId,
            ParticleAcceleratorStrengthProfile resolvedStrength, UUID sourceEmitter) {
        this(ModEntities.PA_PARTICLE.get(), level);
        if (profileId == null || resolvedStrength == null) throw new IllegalArgumentException("resolved PA particle inputs required");
        this.sourceEmitter = sourceEmitter;
        this.strength = resolvedStrength;
        entityData.set(PROFILE, profileId.toString());
        entityData.set(SOURCE_EMITTER_SYNC, sourceEmitter == null ? "" : sourceEmitter.toString());
        entityData.set(VISUAL_STRENGTH, Math.max(0, Math.min(3, resolvedStrength.strength().level())));
    }
    public ResourceLocation profileId() { return ResourceLocation.tryParse(entityData.get(PROFILE)); }
    public UUID sourceEmitterIdentity() {
        String synced = entityData.get(SOURCE_EMITTER_SYNC);
        if (synced != null && !synced.isEmpty()) {
            try { return UUID.fromString(synced); } catch (IllegalArgumentException ignored) { }
        }
        return sourceEmitter;
    }
    @Override public UUID flyByShooterIdentity() { return sourceEmitterIdentity(); }
    @Override public net.minecraft.world.phys.Vec3 flyByPosition() { return position(); }
    @Override public ProjectileFlyByProfile flyByProfile() {
        ParticleAcceleratorParticleProfile profile = resolvedProfile();
        return profile == null ? null : profile.flyBy();
    }
    public int visualStrength() { return entityData.get(VISUAL_STRENGTH); }
    public ParticleAcceleratorParticleProfile resolvedProfile() {
        ResourceLocation id = profileId();
        return id == null ? null : ModParticleAcceleratorParticles.catalog(level()).get(id);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PROFILE, ModParticleAcceleratorParticles.DEFAULT_ID.toString());
        builder.define(SOURCE_EMITTER_SYNC, "");
        builder.define(VISUAL_STRENGTH, 0);
    }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        ParticleAcceleratorParticleProfile p = resolvedProfile();
        if (p == null) { recordContact("invalid-profile-removal", position(), null, 0, 0, 0); discard(); return; }
        if (++age >= p.lifetimeTicks()) { recordContact("lifetime-expiry", position(), null, 0, 0, 0); discard(); return; }
        Vec3 from = position(), to = from.add(getDeltaMovement());
        if (!ProjectileLoadedSweep.isLoadedSpan(server, from, to, p.halfExtent())) {
            overlaps.clear();
            return;
        }
        processContacts(server, p, from, to);
        if (!isRemoved()) {
            setPos(to.x, to.y, to.z);
            refreshOverlaps(server, p, to);
        }
    }

    private void processContacts(ServerLevel server, ParticleAcceleratorParticleProfile p, Vec3 from, Vec3 to) {
        List<Contact> contacts = new ArrayList<>();
        AABB swept = segmentBounds(from, to, p.halfExtent());
        for (Entity e : server.getEntities(this, swept, candidate -> candidate instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity)e;
            if (!living.isAlive() || living.isSpectator() || (sourceEmitter != null && sourceEmitter.equals(living.getUUID()))) continue;
            entryPoint(living.getBoundingBox().inflate(p.halfExtent()), from, to).ifPresent(hit ->
                    contacts.add(new Contact(from.distanceToSqr(hit), living.getUUID(), () -> { recordContact("living-hit", hit, living.getUUID(), 0, 0, 0); hitLiving(living, p); })));
        }
        double maxEventHorizon = ModSingularityGameplay.catalog(level()).values().stream()
                .mapToDouble(profile -> profile.eventHorizonRadiusAtLevel(profile.levelThresholds().size() - 1)).max().orElse(0);
        for (Entity e : server.getEntities(this, segmentBounds(from, to, maxEventHorizon + p.halfExtent()), candidate -> candidate instanceof SingularityEntity)) {
            SingularityEntity singularity = (SingularityEntity)e;
            double radius = singularity.gravityProfile().eventHorizonRadiusAtLevel(singularity.singularityLevel());
            double t = sphereEntry(from, to, singularity.position(), radius + p.halfExtent());
            if (t >= 0) contacts.add(new Contact(t * t * from.distanceToSqr(to), singularity.getUUID(), () -> {
                int food = strength == null ? 0 : strength.singularityFood();
                double before = singularity.energy();
                if (strength != null) singularity.feed(food);
                recordContact("singularity-feed", from.lerp(to, t), singularity.getUUID(), food, before, singularity.energy());
                discard();
            }));
        }
        AABB cells = new AABB(from, to).inflate(p.halfExtent());
        BlockPos.betweenClosedStream(BlockPos.containing(cells.minX,cells.minY,cells.minZ), BlockPos.containing(cells.maxX,cells.maxY,cells.maxZ)).forEach(pos -> {
            if (server.getBlockEntity(pos) instanceof SingularityGeneratorBlockEntity generator) {
                AABB cell = new AABB(pos).inflate(p.halfExtent());
                cell.clip(from,to).ifPresent(hit -> contacts.add(new Contact(from.distanceToSqr(hit), generator.placementIdentity(), () -> {
                    recordContact("generator-hit", hit, generator.placementIdentity(), 0, 0, 0);
                    if (strength != null) generator.receiveParticle(strength);
                    emitImpact(p, hit, ParticleAcceleratorImpactAudio.Contact.GENERATOR, false, false);
                    discard(); // even cooldown, inert, invalid-profile, or zero-charge collisions consume the particle
                })));
            }
        });
        contacts.sort(Comparator.comparingDouble(Contact::distance));
        for (Contact contact : contacts) {
            if (isRemoved()) break;
            if (overlaps.contains(contact.identity)) continue;
            contact.action.run();
        }
    }

    private void hitLiving(LivingEntity living, ParticleAcceleratorParticleProfile p) {
        if (spent) return;
        if (living.isRemoved()) return;
        DamageSystem.DamageApplication result = DamageSystem.applyHealthChangeWithAdmission(living, Map.of(DamageKeys.RADIATION, p.radiationDamage()),
                1f, false, DamageSystem.Cause.RADIATION);
        if (!living.isRemoved()) emitImpact(p, living.position(), ParticleAcceleratorImpactAudio.Contact.LIVING, false, false);
        if (result.status() != DamageSystem.Result.APPLIED) { spent = true; return; }
        if (!result.positiveDamageAdmitted()) { spent = true; return; }
        if (p.deleteOnCollide()) discard();
    }

    private void emitImpact(ParticleAcceleratorParticleProfile profile, Vec3 position,
            ParticleAcceleratorImpactAudio.Contact contact, boolean impactSpent, boolean targetRemoved) {
        var sound = ParticleAcceleratorImpactAudio.sound(profile.impactSound(), contact, impactSpent, targetRemoved);
        if (sound != null && level() instanceof ServerLevel server)
            com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                    server, sound, position.x, position.y, position.z);
    }

    private void refreshOverlaps(ServerLevel server, ParticleAcceleratorParticleProfile p, Vec3 at) {
        Set<UUID> now = new HashSet<>();
        AABB bounds = segmentBounds(at, at, p.halfExtent());
        for (Entity e : server.getEntities(this, bounds, candidate -> candidate instanceof LivingEntity || candidate instanceof SingularityEntity))
            if (e.getBoundingBox().intersects(bounds)) now.add(e.getUUID());
        BlockPos.betweenClosedStream(BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ),BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ)).forEach(pos -> {
            if (server.getBlockEntity(pos) instanceof SingularityGeneratorBlockEntity generator && new AABB(pos).inflate(p.halfExtent()).contains(at)) now.add(generator.placementIdentity());
        });
        overlaps.clear(); overlaps.addAll(now);
    }
    private static AABB segmentBounds(Vec3 from, Vec3 to, double extent) {
        return new AABB(from, to).inflate(extent);
    }
    private static Optional<Vec3> entryPoint(AABB bounds, Vec3 from, Vec3 to) {
        if (bounds.contains(from)) return Optional.of(from);
        return bounds.clip(from, to);
    }
    private static double sphereEntry(Vec3 a, Vec3 b, Vec3 center, double radius) {
        Vec3 d=b.subtract(a), m=a.subtract(center); double aa=d.dot(d), cc=m.dot(m)-radius*radius;
        if (cc<=0) return 0; if (aa<=1e-12) return -1;
        double bb=m.dot(d), disc=bb*bb-aa*cc; if(disc<0) return -1;
        double t=(-bb-Math.sqrt(disc))/aa; return t>=0&&t<=1?t:-1;
    }
    private record Contact(double distance, UUID identity, Runnable action) { }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(AGE,age); tag.putBoolean(SPENT,spent); tag.putString("ParticleProfile", entityData.get(PROFILE));
        if(sourceEmitter!=null)tag.putUUID(SOURCE,sourceEmitter);
        if(strength!=null){tag.putString(STRENGTH,strength.strength().name());tag.putInt(POWER,strength.powerBoxMv());tag.putInt(FOOD,strength.singularityFood());tag.putInt(CHARGE,strength.generatorCharge());}
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        age=Math.max(0,tag.getInt(AGE)); spent=tag.getBoolean(SPENT);
        ResourceLocation profileId = tag.contains("ParticleProfile") ? ResourceLocation.tryParse(tag.getString("ParticleProfile")) : null;
        if (profileId != null) entityData.set(PROFILE, profileId.toString());
        if(tag.hasUUID(SOURCE)) { sourceEmitter=tag.getUUID(SOURCE); entityData.set(SOURCE_EMITTER_SYNC, sourceEmitter.toString()); }
        try { if(tag.contains(STRENGTH)) strength=new ParticleAcceleratorStrengthProfile(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength.valueOf(tag.getString(STRENGTH)),tag.getInt(POWER),tag.getInt(FOOD),tag.getInt(CHARGE)); }
        catch(IllegalArgumentException ignored){strength=null;}
        // Strength visual state is synchronized independently of food/charge server data.
        if(strength!=null)entityData.set(VISUAL_STRENGTH,Math.max(0,Math.min(3,strength.strength().level())));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) { return new ClientboundAddEntityPacket(this,serverEntity); }
    @Override public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        lerpMotion(packet.getXa(), packet.getYa(), packet.getZa());
    }
}
