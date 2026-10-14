package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGravityPulse;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.radiation.RadiationService;
import com.juicyslew.moonstation14.ms14.radiation.RadiationSourceProfile;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Registered singularity energy state, resolved-profile movement and field-face response. */
public final class SingularityEntity extends Entity {
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SYNC_PROFILE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> NEXT_GRAVITY_PULSE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FIRST_GRAVITY_PULSE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.LONG);
    private static final String PROFILE = "GameplayProfile", IDENTITY = "SingularityIdentity", WALK_TIMER = "WalkTimer";
    private UUID identity = UUID.randomUUID();
    private ResourceLocation profileId = ModSingularityGameplay.DEFAULT_ID;
    private double drainElapsed;
    private int walkTimer;
    private long nextGravityPulse;
    private long firstGravityPulse;
    private long sampledPulseTick = Long.MIN_VALUE;
    private double sampledPulseElapsed;
    private boolean initialized;
    private boolean dissipatingSoundPlayed;

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level); noPhysics = true;
        nextGravityPulse = level.getGameTime() + SingularityGravityPulse.PULSE_TICKS;
        firstGravityPulse = nextGravityPulse;
    }

    private SingularityGameplayProfile profile() {
        SingularityGameplayProfile profile = gravityProfileIfReady();
        if (profile == null) throw new IllegalStateException("Missing singularity gameplay prototype " + profileId);
        return profile;
    }
    public SingularityGameplayProfile gravityProfileIfReady() {
        ResourceLocation resolvedId = profileId;
        if (level().isClientSide) {
            try { resolvedId = ResourceLocation.parse(entityData.get(SYNC_PROFILE)); }
            catch (RuntimeException invalidId) { return null; }
        }
        try { return ModSingularityGameplay.catalog(level()).get(resolvedId); }
        catch (RuntimeException notReady) { return null; }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LEVEL, 1); builder.define(ENERGY, 0f);
        builder.define(SYNC_PROFILE, ModSingularityGameplay.DEFAULT_ID.toString());
        builder.define(NEXT_GRAVITY_PULSE, 0L);
        builder.define(FIRST_GRAVITY_PULSE, 0L);
    }
    public int singularityLevel() { return entityData.get(LEVEL); }
    public double energy() { return entityData.get(ENERGY); }
    public UUID identity() { return identity; }
    public ResourceLocation profileId() {
        if (!level().isClientSide) return profileId;
        try { return ResourceLocation.parse(entityData.get(SYNC_PROFILE)); }
        catch (RuntimeException invalidId) { return profileId; }
    }
    /** Resolved profile used by the shared server/prediction gravity kernel. */
    public SingularityGameplayProfile gravityProfile() { return profile(); }
    /** Source-local pulse sample; repeated consumers in a server tick share one advancement. */
    public double gravityPulseElapsed(long tick) {
        if (level().isClientSide) {
            long first = entityData.get(FIRST_GRAVITY_PULSE);
            return first > 0 ? SingularityGravityPulse.predictedElapsedSeconds(first, tick) : 0;
        }
        if (entityData.get(NEXT_GRAVITY_PULSE) == 0) entityData.set(NEXT_GRAVITY_PULSE, nextGravityPulse);
        if (entityData.get(FIRST_GRAVITY_PULSE) == 0) entityData.set(FIRST_GRAVITY_PULSE, firstGravityPulse);
        if (tick == sampledPulseTick) return sampledPulseElapsed;
        sampledPulseTick = tick;
        sampledPulseElapsed = tick >= nextGravityPulse
                ? new SingularityGravityPulse.Schedule(nextGravityPulse).elapsedSeconds(tick) : 0;
        if (sampledPulseElapsed > 0) {
            nextGravityPulse = new SingularityGravityPulse.Schedule(nextGravityPulse).advance(tick).nextTick();
            entityData.set(NEXT_GRAVITY_PULSE, nextGravityPulse);
        }
        return sampledPulseElapsed;
    }

    /** Particle accelerator transfer: unlike startup initialization this adds actual stored energy. */
    public void feed(double amount) {
        if (level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        if (!initialized) initializeFromProfile();
        setEnergy(energy() + amount);
    }
    public void drain(double amount) {
        if (level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        setEnergy(Math.max(0, energy() - amount));
    }
    private void setEnergy(double value) {
        double bounded = Math.max(0, Math.min(Float.MAX_VALUE, value));
        entityData.set(ENERGY, (float) bounded);
        entityData.set(LEVEL, profile().levelForEnergy(bounded));
        if (bounded <= 0) { playDissipation(); discard(); }
    }
    @Override public void tick() {
        if (!(level() instanceof ServerLevel server)) { super.tick(); return; }
        if (entityData.get(NEXT_GRAVITY_PULSE) == 0) entityData.set(NEXT_GRAVITY_PULSE, nextGravityPulse);
        if (entityData.get(FIRST_GRAVITY_PULSE) == 0) entityData.set(FIRST_GRAVITY_PULSE, firstGravityPulse);
        if (!initialized) {
            initializeFromProfile();
            walkTimer = 40 + random.nextInt(61);
        }
        // Entity.tick handles bookkeeping/interpolation but does not integrate this entity's movement.
        super.tick();
        if (isRemoved()) return;
        drainElapsed += .05;
        setEnergy(profile().energyAfterSeconds(energy(), singularityLevel(), .05));
        if (isRemoved()) return;
        // The upstream GravityWell schedule belongs to the source, not to nearby consumers.
        // Sample once per server tick even in empty space; target adapters share this cached sample.
        gravityPulseElapsed(server.getGameTime());
        consumeHorizonBodies(server);
        if (isRemoved()) return;
        if (--walkTimer <= 0) {
            double speed = profile().minSpeedAtLevel(singularityLevel())
                    + random.nextDouble() * (profile().maxSpeedAtLevel(singularityLevel()) - profile().minSpeedAtLevel(singularityLevel()));
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1, z = random.nextDouble() * 2 - 1;
            Vec3 direction = new Vec3(x, y, z).normalize();
            setDeltaMovement(direction.scale(speed));
            walkTimer = 40 + random.nextInt(61);
        }
        Vec3 velocity = getDeltaMovement();
        Vec3 displacement = velocity.scale(.05);
        if (singularityLevel() < 5) moveAgainstFields(server, displacement);
        else move(net.minecraft.world.entity.MoverType.SELF, displacement);
        if (!isRemoved()) {
            consumeHorizonBodies(server);
            updateRadiation(server);
        }
    }
    /** Living-body-only Minecraft adaptation of SS14 EventHorizon; never consumes blocks or inventory. */
    private void consumeHorizonBodies(ServerLevel server) {
        var horizon = profile().eventHorizon();
        if (!horizon.consumeEntities()) return;
        double radius = profile().eventHorizonRadiusAtLevel(singularityLevel());
        if (radius <= 0) return;
        var center = position();
        var box = getBoundingBox().inflate(radius);
        for (var body : server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                candidate -> candidate.isAlive() && !candidate.isSpectator() && !candidate.noPhysics
                        && candidate.isAddedToLevel()
                        && !(candidate instanceof net.minecraft.server.level.ServerPlayer player
                            && com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.isCarrier(player)))) {
            if (!com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry
                    .touches(center, radius, body.getBoundingBox())) continue;
            // Set zero health then use the native death event without a MAX_FLOAT damage delta, which
            // overflows the typed-damage projection. Native MobStateSystem still owns death admission.
            body.setHealth(0);
            body.die(body.damageSources().genericKill());
            if (body instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor deathState
                    && deathState.ms14$isDead())
                feed(horizon.entityEnergy());
        }
    }
    private void initializeFromProfile() {
        boolean startup = !initialized;
        initialized = true;
        if (energy() <= 0) setEnergy(profile().initialEnergy());
        if (startup && level() instanceof ServerLevel server && profile().sounds().formation() != null)
            com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(server,
                    profile().sounds().formation(), getX(), getY(), getZ());
    }
    /** Generator startup uses the same resolved gameplay profile and initialization path as normal entity startup. */
    public void initializeFromGenerator() { if (level().isClientSide) return; initializeFromProfile(); }
    public void initializeFromGenerator(ResourceLocation configuredProfile) {
        if (level().isClientSide || configuredProfile == null) return;
        profileId = configuredProfile;
        entityData.set(SYNC_PROFILE, profileId.toString());
        initializeFromProfile();
    }
    private void updateRadiation(ServerLevel server) {
        if (isRemoved() || !isAddedToLevel() || !server.hasChunkAt(blockPosition())) return;
        double intensity = singularityLevel() * profile().radsPerLevel();
        RadiationService.INSTANCE.register(server, identity, position(), new RadiationSourceProfile(intensity, profile().radiation().slope()));
    }
    /** Loaded field faces alone constrain movement. Matter blocks deliberately remain non-colliding here. */
    private void moveAgainstFields(ServerLevel server, Vec3 displacement) {
        Vec3 start = position(), remaining = displacement;
        for (int i=0; i<8 && remaining.lengthSqr()>1.0e-10; i++) {
            Vec3 end = start.add(remaining);
            var contact = FieldContainmentRuntime.firstFaceHit(server, start, end, profile().eventHorizonRadiusAtLevel(singularityLevel()));
            if (contact.isEmpty()) { start = end; remaining = Vec3.ZERO; break; }
            var hit = contact.get();
            Vec3 normal = new Vec3(hit.normal().getStepX(), hit.normal().getStepY(), hit.normal().getStepZ());
            start = start.add(remaining.scale(Math.max(0, hit.segmentFraction() - 1.0e-5)));
            Vec3 rest = remaining.scale(1 - hit.segmentFraction());
            Vec3 reflectedVelocity = getDeltaMovement().subtract(normal.scale(2 * getDeltaMovement().dot(normal))).scale(.8);
            setDeltaMovement(reflectedVelocity);
            remaining = rest.subtract(normal.scale(2 * rest.dot(normal))).scale(.8);
        }
        move(net.minecraft.world.entity.MoverType.SELF, start.subtract(position()));
    }
    @Override public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            RadiationService.INSTANCE.remove(server, identity);
            if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) playDissipation();
        }
        super.remove(reason);
    }
    private void playDissipation() {
        if (dissipatingSoundPlayed || !(level() instanceof ServerLevel server)) return;
        dissipatingSoundPlayed = true;
        var resolved = gravityProfileIfReady();
        if (resolved != null && resolved.sounds().dissipation() != null)
            com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(server,
                    resolved.sounds().dissipation(), getX(), getY(), getZ());
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putUUID(IDENTITY, identity); tag.putString(PROFILE, profileId.toString());
        tag.putLong("NextGravityPulse", nextGravityPulse);
        tag.putLong("FirstGravityPulse", firstGravityPulse);
        tag.putDouble("DrainElapsed", drainElapsed); tag.putInt(WALK_TIMER, walkTimer);
        tag.putDouble("Energy", energy()); tag.putInt("Level", singularityLevel());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(IDENTITY)) identity = tag.getUUID(IDENTITY);
        if (tag.contains(PROFILE)) profileId = ResourceLocation.parse(tag.getString(PROFILE));
        entityData.set(SYNC_PROFILE, profileId.toString());
        nextGravityPulse = tag.contains("NextGravityPulse") ? tag.getLong("NextGravityPulse")
                : level().getGameTime() + SingularityGravityPulse.PULSE_TICKS;
        firstGravityPulse = tag.contains("FirstGravityPulse") ? tag.getLong("FirstGravityPulse") : nextGravityPulse;
        entityData.set(NEXT_GRAVITY_PULSE, nextGravityPulse);
        entityData.set(FIRST_GRAVITY_PULSE, firstGravityPulse);
        drainElapsed = Math.max(0, tag.getDouble("DrainElapsed")); walkTimer = Math.max(0, tag.getInt(WALK_TIMER));
        entityData.set(ENERGY, Math.max(0, tag.getFloat("Energy")));
        entityData.set(LEVEL, Math.max(0, tag.getInt("Level"))); initialized = true;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}

