package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import com.juicyslew.moonstation14.ms14.singularity.SingularityGameplayProfile;
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
public final class SingularityEntity extends Entity implements com.juicyslew.moonstation14.ms14.singularity.GeneratorSpawnTarget, com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorEnergyReceiver {
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> SYNC_PROFILE = SynchedEntityData.defineId(SingularityEntity.class, EntityDataSerializers.STRING);
    private static final String PROFILE = "GameplayProfile", IDENTITY = "SingularityIdentity", WALK_TIMER = "WalkTimer";
    private UUID identity = UUID.randomUUID();
    private ResourceLocation profileId = ModSingularityGameplay.DEFAULT_ID;
    private double drainElapsed;
    private int walkTimer;
    private boolean initialized;
    private boolean dissipatingSoundPlayed;
    private Vec3 pendingWalkBias = Vec3.ZERO;
    private long nextHorizonWave;
    private boolean previousFieldContained;
    private boolean escapePhaseStarted;
    private long escapeUntilTick;

    /** Adds a one-step directional bias; it changes the next random walk, not current velocity. */
    public void addWalkBias(Vec3 bias) {
        if (bias != null && Double.isFinite(bias.x) && Double.isFinite(bias.y) && Double.isFinite(bias.z))
            pendingWalkBias = pendingWalkBias.add(bias);
    }

    public SingularityEntity(EntityType<? extends SingularityEntity> type, Level level) {
        super(type, level); noPhysics = true;
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

    /** Particle accelerator transfer: unlike startup initialization this adds actual stored energy. */
    public void feed(double amount) {
        if (level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        if (!initialized) initializeFromProfile();
        setEnergy(energy() + amount);
    }
    @Override public double particleContactRadius() { return gravityProfile().eventHorizonRadiusAtLevel(singularityLevel()); }
    @Override public double particleEnergy() { return energy(); }
    @Override public void receiveParticleEnergy(com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrengthProfile strength) {
        if (strength != null) feed(strength.singularityFood());
    }
    public void drain(double amount) {
        if (level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        setEnergy(Math.max(0, energy() - amount));
    }
    private void setEnergy(double value) {
        int previousLevel = singularityLevel();
        double bounded = Math.max(0, Math.min(Float.MAX_VALUE, value));
        entityData.set(ENERGY, (float) bounded);
        int nextLevel = profile().levelForEnergy(bounded);
        entityData.set(LEVEL, nextLevel);
        if (com.juicyslew.moonstation14.ms14.singularity.SingularityWalkMath.stageDropResetsVelocity(previousLevel, nextLevel))
            setDeltaMovement(Vec3.ZERO);
        if (bounded <= 0) { playDissipation(); discard(); }
    }
    @Override public void tick() {
        if (!(level() instanceof ServerLevel server)) { super.tick(); return; }
        if (!initialized) {
            initializeFromProfile();
            var walk = profile().randomWalk();
            walkTimer = walk.minimumIntervalTicks() + random.nextInt(walk.maximumIntervalTicks() - walk.minimumIntervalTicks() + 1);
            if (nextHorizonWave <= 0) nextHorizonWave = server.getGameTime();
        }
        if (!escapePhaseStarted) {
            previousFieldContained = fieldContains(server);
            if (!previousFieldContained) escapeUntilTick = server.getGameTime() + profile().randomWalk().initialEscapeTicks();
            escapePhaseStarted = true;
        }
        // Entity.tick handles bookkeeping/interpolation but does not integrate this entity's movement.
        super.tick();
        if (isRemoved()) return;
        drainElapsed += .05;
        setEnergy(profile().energyAfterSeconds(energy(), singularityLevel(), .05));
        if (isRemoved()) return;
        consumeHorizonBodies(server);
        updateEscapePhase(server);
        if (server.getGameTime() >= nextHorizonWave) {
            consumeHorizonMatter(server);
            nextHorizonWave = horizonWaveSchedule(server.getGameTime(), profile().eventHorizon().consumePeriodSeconds());
        }
        if (isRemoved()) return;
        if (--walkTimer <= 0) {
            var walk = profile().randomWalk();
            double speed = profile().minSpeedAtLevel(singularityLevel())
                    + random.nextDouble() * (profile().maxSpeedAtLevel(singularityLevel()) - profile().minSpeedAtLevel(singularityLevel()));
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1, z = random.nextDouble() * 2 - 1;
            Vec3 direction = com.juicyslew.moonstation14.ms14.singularity.SingularityWalkMath.direction(
                    x, y, z, walk.verticalScale(),
                    com.juicyslew.moonstation14.ms14.singularity.SingularityWalkMath.escapeActive(server.getGameTime(), escapeUntilTick)
                            ? walk.initialEscapeHorizontalBias() : 0, pendingWalkBias);
            pendingWalkBias = Vec3.ZERO;
            setDeltaMovement(direction.scale(speed));
            walkTimer = walk.minimumIntervalTicks() + random.nextInt(walk.maximumIntervalTicks() - walk.minimumIntervalTicks() + 1);
        }
        Vec3 velocity = getDeltaMovement();
        Vec3 displacement = velocity.scale(.05);
        if (singularityLevel() < 5) moveAgainstFields(server, displacement);
        else move(net.minecraft.world.entity.MoverType.SELF, displacement);
        if (!isRemoved()) {
            consumeHorizonBodies(server);
            updateEscapePhase(server);
            updateRadiation(server);
        }
    }
    private static long horizonWaveSchedule(long now, double periodSeconds) {
        double ticks = Math.ceil(periodSeconds * 20);
        if (!Double.isFinite(ticks) || ticks < 1 || ticks > Long.MAX_VALUE - Math.max(0, now))
            throw new IllegalArgumentException("invalid singularity horizon period");
        return now + (long) ticks;
    }
    /** Upstream contact response is every source tick, independent of the periodic matter wave. */
    private void consumeHorizonBodies(ServerLevel server) {
        var horizon = profile().eventHorizon();
        if (!horizon.consumeEntities()) return;
        double radius = profile().eventHorizonRadiusAtLevel(singularityLevel());
        if (radius <= 0) return;
        var center = position();
        var box = getBoundingBox().inflate(radius);
        for (var body : server.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption::eligible)) {
            boolean playerCore = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy
                    .isPlayerCharacterBody(body, server);
            double lethalRadius = playerCore ? profile().playerLethalRadiusAtLevel(singularityLevel()) : radius;
            if (!com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption
                    .touches(center, lethalRadius, body)) continue;
            // Set zero health then use the native death event without a MAX_FLOAT damage delta, which
            // overflows the typed-damage projection. Native MobStateSystem still owns death admission.
            if (com.juicyslew.moonstation14.ms14.singularity.ReactorContactConsumption.kill(server, body))
                feed(horizon.entityEnergy());
        }
    }
    /** 0.5-second upstream event-horizon wave adapted to loaded Minecraft entities and block cells. */
    private void consumeHorizonMatter(ServerLevel server) {
        var horizon = profile().eventHorizon();
        double radius = profile().eventHorizonRadiusAtLevel(singularityLevel());
        if (radius <= 0) return;
        var center = position();
        var box = getBoundingBox().inflate(radius);
        if (horizon.consumeEntities()) for (var target : server.getEntitiesOfClass(Entity.class, box,
                candidate -> candidate != this && candidate.isAlive() && candidate.isAddedToLevel()
                        && !(candidate instanceof net.minecraft.world.entity.LivingEntity)
                        && (candidate instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable
                            || candidate instanceof SingularityEntity))) {
            if (isRemoved()) break;
            if (!com.juicyslew.moonstation14.ms14.singularity.SingularityHorizonGeometry.touches(center, radius, target.getBoundingBox())) continue;
            if (!(target instanceof SingularityEntity) && target instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item
                    && com.juicyslew.moonstation14.ms14.singularity.SingularityConsumptionSafety.hasOwnedPayload(item.getItem())) continue;
            double replacement = foodReplacement(target, energy(), horizon.entityEnergy());
            if (!supportedEnergy(replacement)) continue;
            target.discard();
            if (target.isRemoved()) setEnergy(replacement);
        }
        if (!horizon.consumeTiles()) return;
        int extent = (int) Math.ceil(radius);
        net.minecraft.core.BlockPos.betweenClosedStream(blockPosition().offset(-extent, -extent, -extent), blockPosition().offset(extent, extent, extent)).forEach(pos -> {
            if (isRemoved()) return;
            var state = server.getBlockState(pos);
            var blockEntity = server.getBlockEntity(pos);
            if (!com.juicyslew.moonstation14.ms14.singularity.ReactorBlockConsumption.eligible(
                    server,pos,profile().canBreachAtLevel(singularityLevel()))) return;
            if (!blockIntersectsSphere(pos, center, radius)) return;
            var lowYieldTag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_low_yield_terrain"));
            double credit = state.is(lowYieldTag) ? horizon.lowYieldTerrainEnergy() : horizon.terrainEnergy();
            if (blockEntity instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food) {
                double resultingEnergy = foodReplacement(food, energy());
                if (!Double.isFinite(resultingEnergy)) return;
                credit = resultingEnergy - energy();
            }
            double replacement = energy() + credit;
            if (!supportedEnergy(replacement)) return;
            if (com.juicyslew.moonstation14.ms14.singularity.ReactorBlockConsumption.remove(
                    server,pos,profile().canBreachAtLevel(singularityLevel()))) {
                setEnergy(replacement);
            }
        });
    }
    private static boolean supportedEnergy(double value) { return Double.isFinite(value) && value >= 0 && value <= Float.MAX_VALUE; }
    private static boolean blockIntersectsSphere(net.minecraft.core.BlockPos pos, Vec3 center, double radius) {
        double x = Math.max(pos.getX(), Math.min(center.x, pos.getX() + 1d));
        double y = Math.max(pos.getY(), Math.min(center.y, pos.getY() + 1d));
        double z = Math.max(pos.getZ(), Math.min(center.z, pos.getZ() + 1d));
        return center.distanceToSqr(x, y, z) <= radius * radius;
    }
    private double foodReplacement(Entity target, double current, double ordinaryEnergy) {
        if (target instanceof SingularityEntity other) {
            if (other == this || other.isRemoved()) return current;
            return current + other.energy();
        }
        if (target instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food) {
            return foodReplacement(food, current);
        }
        if (!(target instanceof com.juicyslew.moonstation14.entities.LooseItemEntity item)) return Double.NaN;
        if (item.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.singularity.SingularityFood food)
            return foodReplacement(food, current);
        return com.juicyslew.moonstation14.ms14.singularity.SingularityFoodMath.afterOrdinaryEntity(current, ordinaryEnergy);
    }
    private static double foodReplacement(com.juicyslew.moonstation14.ms14.singularity.SingularityFood food, double current) {
        double flat = food.singularityFoodEnergy(), factor = food.singularityFoodFactor();
        if (!Double.isFinite(flat) || !Double.isFinite(factor) || factor < 0) return Double.NaN;
        return com.juicyslew.moonstation14.ms14.singularity.SingularityFoodMath.after(current, flat, factor);
    }
    private boolean fieldContains(ServerLevel server) {
        if (profile().canBreachAtLevel(singularityLevel())) return false;
        Vec3 p = position();
        return FieldContainmentRuntime.closedVolumes(server).stream().anyMatch(v ->
                p.x >= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.min().getX())
                && p.x <= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.max().getX())
                && p.y >= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.min().getY())
                && p.y <= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.max().getY())
                && p.z >= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.min().getZ())
                && p.z <= com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry.center(v.max().getZ()));
    }
    private void updateEscapePhase(ServerLevel server) {
        boolean contained = fieldContains(server);
        if (previousFieldContained && !contained)
            escapeUntilTick = server.getGameTime() + profile().randomWalk().initialEscapeTicks();
        previousFieldContained = contained;
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
    @Override public boolean initializeSourceProfile(ResourceLocation configuredProfile) {
        if (configuredProfile == null || level().isClientSide) return false;
        try { ModSingularityGameplay.catalog(level()).get(configuredProfile); initializeFromGenerator(configuredProfile); return true; }
        catch (RuntimeException missing) { return false; }
    }
    /** Explicit operator-fixture initialization; subsequent ticks retain ordinary drain and movement. */
    public void initializeForGravityFixture(ResourceLocation configuredProfile,double fixtureEnergy) {
        if(level().isClientSide||configuredProfile==null||!Double.isFinite(fixtureEnergy)||fixtureEnergy<=0)return;
        profileId=configuredProfile; entityData.set(SYNC_PROFILE,profileId.toString()); initialized=true;
        setEnergy(fixtureEnergy);
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
        tag.putDouble("DrainElapsed", drainElapsed); tag.putInt(WALK_TIMER, walkTimer);
        tag.putDouble("Energy", energy()); tag.putInt("Level", singularityLevel());
        tag.putLong("NextHorizonWave", nextHorizonWave);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID(IDENTITY)) identity = tag.getUUID(IDENTITY);
        if (tag.contains(PROFILE)) profileId = ResourceLocation.parse(tag.getString(PROFILE));
        entityData.set(SYNC_PROFILE, profileId.toString());
        nextHorizonWave = tag.contains("NextHorizonWave") ? tag.getLong("NextHorizonWave") : 0;
        drainElapsed = Math.max(0, tag.getDouble("DrainElapsed")); walkTimer = Math.max(0, tag.getInt(WALK_TIMER));
        entityData.set(ENERGY, Math.max(0, tag.getFloat("Energy")));
        entityData.set(LEVEL, Math.max(0, tag.getInt("Level"))); initialized = true;
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}

