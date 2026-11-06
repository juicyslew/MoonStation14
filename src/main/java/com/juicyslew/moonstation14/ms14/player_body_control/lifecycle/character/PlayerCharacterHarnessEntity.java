package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** Persistent, ordinary-physics Mob type reserved for future player characters. */
public final class PlayerCharacterHarnessEntity extends Mob implements com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorPending {
    private static final String BINDING_KEY = "Moonstation14PlayerCharacterBinding";
    private static final String APPEARANCE_KEY = "Moonstation14PlayerCharacterAppearance";
    private static final String BODY_SHAPE_KEY = "Moonstation14PlayerCharacterBodyShape";
    private static final String OFFLINE_SINCE_KEY = "Moonstation14PlayerCharacterOfflineSinceMillis";
    private static final String CONFIRMED_DEATH_KEY = "Moonstation14PlayerCharacterConfirmedDeath";
    public static final long OFFLINE_BADGE_DELAY_MILLIS = 60_000L;
    private static final EntityDataAccessor<Integer> APPEARANCE = SynchedEntityData.defineId(
            PlayerCharacterHarnessEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BODY_SHAPE = SynchedEntityData.defineId(
            PlayerCharacterHarnessEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> OFFLINE_BADGE = SynchedEntityData.defineId(
            PlayerCharacterHarnessEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CONFIRMED_DEATH = SynchedEntityData.defineId(
            PlayerCharacterHarnessEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<CompoundTag> ANGULAR_RECOVERY = SynchedEntityData.defineId(
            PlayerCharacterHarnessEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State predictedAngularRecoveryState;
    private boolean hasPredictedAngularRecoveryState;
    private Long offlineSinceMillis;
    // Display-only observation: parked OFFLINE/DEAD_CLAIM time is not time spent disconnected.
    private Long disconnectedSinceMillis;
    private boolean invalidOfflineTimestamp;
    private PlayerCharacterBinding playerCharacterBinding;
    private boolean invalidUnbindable;
    private Tag invalidBindingEvidence;
    private Tag invalidAppearanceEvidence;
    private Tag invalidBodyShapeEvidence;
    private final com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState frameAnchorState = new com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState();
    private com.juicyslew.moonstation14.ms14.movement.AirMotionReference airMotionReference;
    /** Transient physics reference; deliberately not persisted or synchronized. */
    public java.util.Optional<com.juicyslew.moonstation14.ms14.movement.AirMotionReference> airMotionReference() {
        return java.util.Optional.ofNullable(airMotionReference);
    }
    public void setAirMotionReference(com.juicyslew.moonstation14.ms14.movement.AirMotionReference reference) {
        airMotionReference = reference;
    }
    @Override public com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState sable$frameAnchorState() { return frameAnchorState; }

    public PlayerCharacterHarnessEntity(EntityType<? extends PlayerCharacterHarnessEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    /** A character body (including an unclaimed body or corpse) is never a disposable wild Mob. */
    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /**
     * Keep this character's confirmed corpse in the world. Vanilla's default implementation removes
     * dead LivingEntities after twenty ticks; this entity intentionally has no such cleanup policy.
     */
    @Override
    protected void tickDeath() {
        if (deathTime < 20) deathTime++;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (!level().isClientSide) setAngularRecoveryState(java.util.Optional.empty());
        if (!wasDead && dead && !level().isClientSide) {
            entityData.set(CONFIRMED_DEATH, true);
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterDeathHandler
                    .onConfirmedDeath(this);
        }
    }

    /** True after vanilla has accepted this entity's first server-side death. */
    public boolean hasConfirmedDeath() {
        return entityData.get(CONFIRMED_DEATH);
    }

    /** Clear an accepted-death signal only after an authoritative medical revival completed. */
    public boolean clearConfirmedDeathForRevival() {
        if (level().isClientSide || dead || !isAlive()
                || (!com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isAlive(this)
                && !com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(this))) return false;
        entityData.set(CONFIRMED_DEATH, false);
        return true;
    }

    /** Render only an accepted death, never a disconnected or merely zero-health body. */
    public boolean shouldRenderCorpsePose() {
        return hasConfirmedDeath() && !isAlive();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, PlayerCharacterAppearance.DEFAULT.index());
        builder.define(BODY_SHAPE, PlayerCharacterBodyShape.WIDE.index());
        builder.define(OFFLINE_BADGE, false);
        builder.define(CONFIRMED_DEATH, false);
        builder.define(ANGULAR_RECOVERY, new CompoundTag());
    }

    public java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> angularRecoveryState() {
        return hasPredictedAngularRecoveryState ? java.util.Optional.ofNullable(predictedAngularRecoveryState)
                : authoritativeAngularRecoveryState();
    }

    public java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> authoritativeAngularRecoveryState() {
        CompoundTag tag = entityData.get(ANGULAR_RECOVERY);
        if (!tag.contains("state", Tag.TAG_COMPOUND)) return java.util.Optional.empty();
        CompoundTag state = tag.getCompound("state");
        var q = new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Quat(
                state.getDouble("qw"), state.getDouble("qx"), state.getDouble("qy"), state.getDouble("qz"));
        var w = new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.Vec3(
                state.getDouble("wx"), state.getDouble("wy"), state.getDouble("wz"));
        return java.util.Optional.of(new com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State(
                q, w, state.getDouble("airborne"), state.getBoolean("active")));
    }

    public void setAngularRecoveryState(java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> value) {
        java.util.Objects.requireNonNull(value, "value");
        CompoundTag tag = new CompoundTag();
        value.ifPresent(state -> {
            CompoundTag data = new CompoundTag(); var q = state.orientation(); var w = state.worldAngularVelocity();
            data.putDouble("qw", q.w()); data.putDouble("qx", q.x()); data.putDouble("qy", q.y()); data.putDouble("qz", q.z());
            data.putDouble("wx", w.x()); data.putDouble("wy", w.y()); data.putDouble("wz", w.z());
            data.putDouble("airborne", state.airborneSeconds()); data.putBoolean("active", state.recoveryActive()); tag.put("state", data);
        });
        entityData.set(ANGULAR_RECOVERY, tag);
    }

    public void setPredictedAngularRecoveryState(java.util.Optional<com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel.State> value) {
        hasPredictedAngularRecoveryState = true;
        predictedAngularRecoveryState = java.util.Objects.requireNonNull(value, "value").orElse(null);
    }
    public void clearPredictedAngularRecoveryState() { hasPredictedAngularRecoveryState = false; predictedAngularRecoveryState = null; }

    /** Apply inertial body orientation once to logical head look; yaw/pitch remain player input. */
    @Override
    public net.minecraft.world.phys.Vec3 getLookAngle() {
        var look = com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook.worldLook(
                angularRecoveryState().orElse(null), getYRot(), getXRot());
        return new net.minecraft.world.phys.Vec3(look.x(), look.y(), look.z());
    }

    /** A same-entity teleport starts a fresh inertial recovery basis. */
    @Override
    public void teleportTo(double x, double y, double z) {
        if (!level().isClientSide) setAngularRecoveryState(java.util.Optional.empty());
        clearPredictedAngularRecoveryState();
        super.teleportTo(x, y, z);
    }

    public PlayerCharacterAppearance appearance() {
        return PlayerCharacterAppearance.fromIndex(entityData.get(APPEARANCE));
    }

    void setAppearance(PlayerCharacterAppearance appearance) {
        entityData.set(APPEARANCE, appearance.index());
        invalidAppearanceEvidence = null;
    }

    public PlayerCharacterBodyShape bodyShape() {
        return PlayerCharacterBodyShape.fromIndex(entityData.get(BODY_SHAPE));
    }

    public boolean hasOfflineBadge() { return entityData.get(OFFLINE_BADGE); }

    public Long offlineSinceMillis() { return offlineSinceMillis; }

    /** Lifecycle server callers set this only after proving the exact durable owner/body transition. */
    public void setOfflineSinceMillis(long timestamp) {
        if (timestamp < 0) {
            invalidOfflineTimestamp = true;
            offlineSinceMillis = null;
            refreshOfflineBadge(System.currentTimeMillis());
            return;
        }
        offlineSinceMillis = timestamp;
        invalidOfflineTimestamp = false;
        refreshOfflineBadge(System.currentTimeMillis());
    }

    /** Clear only after the exact durable OFFLINE-to-ACTIVE reconnect succeeds. */
    public void clearOfflineSinceMillis() {
        offlineSinceMillis = null;
        invalidOfflineTimestamp = false;
        disconnectedSinceMillis = null;
        entityData.set(OFFLINE_BADGE, false);
    }

    public static boolean offlineBadgeDue(long offlineSinceMillis, long nowMillis) {
        return offlineSinceMillis >= 0 && nowMillis >= offlineSinceMillis
                && nowMillis - offlineSinceMillis >= OFFLINE_BADGE_DELAY_MILLIS;
    }

    /** Display policy only; neither this nor the transient disconnect timer defines lifecycle state. */
    public static boolean offlineBadgeVisible(UUID boundAccount, Long persistedOfflineSince,
                                              Long disconnectedSince, boolean boundAccountConnected,
                                              long nowMillis) {
        return boundAccount != null && persistedOfflineSince != null && persistedOfflineSince >= 0
                && !boundAccountConnected && disconnectedSince != null
                && offlineBadgeDue(disconnectedSince, nowMillis);
    }

    private void refreshOfflineBadge(long nowMillis) {
        if (!(level() instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) return;
        UUID boundAccount = invalidUnbindable || playerCharacterBinding == null
                ? null : playerCharacterBinding.accountId();
        if (boundAccount == null || invalidOfflineTimestamp || offlineSinceMillis == null) {
            disconnectedSinceMillis = null;
            entityData.set(OFFLINE_BADGE, false);
            return;
        }
        // The server player list is global across dimensions and includes ghosts and Creative operators.
        boolean connected = serverLevel.getServer().getPlayerList().getPlayer(boundAccount) != null;
        disconnectedSinceMillis = connected ? null
                : disconnectedSinceMillis == null ? nowMillis : disconnectedSinceMillis;
        entityData.set(OFFLINE_BADGE, offlineBadgeVisible(boundAccount, offlineSinceMillis,
                disconnectedSinceMillis, connected, nowMillis));
    }

    @Override
    public void tick() {
        if (!level().isClientSide)
            com.juicyslew.moonstation14.ms14.character.CharacterAngularRecoverySystem.clearIfSuperseded(this);
        if (!level().isClientSide) frameAnchorState.traceLifecycle((ServerLevel) level(), this,
                com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState.DiagnosticPhase.BEFORE_BODY_SUPER);
        if (!level().isClientSide && frameAnchorState.pending()) return;
        super.tick();
        if (!level().isClientSide) frameAnchorState.traceLifecycle((ServerLevel) level(), this,
                com.juicyslew.moonstation14.ms14.shuttle.sable.FrameAnchorState.DiagnosticPhase.AFTER_BODY_SUPER);
        if (!level().isClientSide) refreshOfflineBadge(System.currentTimeMillis());
    }

    void setBodyShape(PlayerCharacterBodyShape shape) {
        entityData.set(BODY_SHAPE, shape.index());
        invalidBodyShapeEvidence = null;
    }

    /** Read-only per-instance identity for a future exact owner/Mind/body proof. */
    public PlayerCharacterBinding playerCharacterBinding() {
        return playerCharacterBinding;
    }

    /** Whether saved identity data was malformed and this instance is permanently unbindable. */
    public boolean hasInvalidSavedBinding() {
        return invalidUnbindable;
    }

    boolean hasPlayerCharacterBinding() {
        return playerCharacterBinding != null;
    }

    boolean isInvalidUnbindable() {
        return invalidUnbindable;
    }

    void setPlayerCharacterBinding(PlayerCharacterBinding binding) {
        playerCharacterBinding = binding;
        disconnectedSinceMillis = null;
        entityData.set(OFFLINE_BADGE, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (level() instanceof ServerLevel server) frameAnchorState.write(tag, server, this);
        if (invalidUnbindable) tag.put(BINDING_KEY, invalidBindingEvidence.copy());
        else if (playerCharacterBinding != null) tag.put(BINDING_KEY, playerCharacterBinding.save());
        if (invalidAppearanceEvidence != null) tag.put(APPEARANCE_KEY, invalidAppearanceEvidence.copy());
        else tag.putInt(APPEARANCE_KEY, appearance().index());
        if (invalidBodyShapeEvidence != null) tag.put(BODY_SHAPE_KEY, invalidBodyShapeEvidence.copy());
        else tag.putInt(BODY_SHAPE_KEY, bodyShape().index());
        if (invalidOfflineTimestamp) tag.putString(OFFLINE_SINCE_KEY, "invalid");
        else if (offlineSinceMillis != null) tag.putLong(OFFLINE_SINCE_KEY, offlineSinceMillis);
        if (hasConfirmedDeath()) tag.putBoolean(CONFIRMED_DEATH_KEY, true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        frameAnchorState.read(tag);
        // Vanilla saves Health and DeathTime, but neither proves that die() was accepted.
        // Never infer confirmation from them, including on old saves without this key.
        entityData.set(CONFIRMED_DEATH, tag.contains(CONFIRMED_DEATH_KEY, Tag.TAG_BYTE)
                && tag.getBoolean(CONFIRMED_DEATH_KEY));
        if (hasConfirmedDeath()) setHealth(0.0F);
        // A player-controlled character body must never run voluntary Mob AI, even if
        // older or externally edited entity data explicitly saved NoAI as false.
        setNoAi(true);
        playerCharacterBinding = null;
        offlineSinceMillis = null;
        disconnectedSinceMillis = null;
        invalidOfflineTimestamp = false;
        entityData.set(OFFLINE_BADGE, false);
        invalidUnbindable = false;
        invalidBindingEvidence = null;
        if (tag.contains(BINDING_KEY)) {
            Tag savedBinding = tag.get(BINDING_KEY);
            try {
                if (!(savedBinding instanceof CompoundTag compound))
                    throw new IllegalArgumentException("Saved player-character binding is not a compound");
                playerCharacterBinding = PlayerCharacterBinding.load(compound);
            } catch (RuntimeException invalid) {
                invalidUnbindable = true;
                invalidBindingEvidence = savedBinding.copy();
            }
        }

        invalidAppearanceEvidence = null;
        invalidBodyShapeEvidence = null;
        if (!tag.contains(BODY_SHAPE_KEY)) {
            setBodyShape(PlayerCharacterBodyShape.WIDE);
        } else {
            Tag savedBodyShape = tag.get(BODY_SHAPE_KEY);
            if (savedBodyShape instanceof net.minecraft.nbt.IntTag intTag
                    && PlayerCharacterBodyShape.fromIndex(intTag.getAsInt()) != null) {
                setBodyShape(PlayerCharacterBodyShape.fromIndex(intTag.getAsInt()));
            } else {
                setBodyShape(PlayerCharacterBodyShape.WIDE);
                invalidBodyShapeEvidence = savedBodyShape.copy();
            }
        }
        if (!tag.contains(APPEARANCE_KEY)) {
            setAppearance(PlayerCharacterAppearance.DEFAULT);
        } else {
            Tag savedAppearance = tag.get(APPEARANCE_KEY);
            if (savedAppearance instanceof net.minecraft.nbt.IntTag intTag
                && PlayerCharacterAppearance.fromIndex(intTag.getAsInt()) != null) {
                setAppearance(PlayerCharacterAppearance.fromIndex(intTag.getAsInt()));
            } else {
                setAppearance(PlayerCharacterAppearance.DEFAULT);
                invalidAppearanceEvidence = savedAppearance.copy();
            }
        }
        if (tag.contains(OFFLINE_SINCE_KEY)) {
            Tag savedOfflineSince = tag.get(OFFLINE_SINCE_KEY);
            if (savedOfflineSince instanceof net.minecraft.nbt.LongTag longTag && longTag.getAsLong() >= 0) {
                offlineSinceMillis = longTag.getAsLong();
            } else {
                invalidOfflineTimestamp = true;
            }
        }
    }
}
