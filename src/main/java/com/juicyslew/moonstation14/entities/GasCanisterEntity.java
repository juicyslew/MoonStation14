package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasCanisterProfile;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasCanisterState;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasCanisters;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import java.util.Optional;

/** World canister: the proxy stack is presentation only; the tank slot owns gas tanks. */
public final class GasCanisterEntity extends LooseItemEntity implements CanisterRemovalLifecycle {
    private static final EntityDataAccessor<String> SYNC_PROFILE = SynchedEntityData.defineId(GasCanisterEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SYNC_PRESSURE_BAND = SynchedEntityData.defineId(GasCanisterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SYNC_TANK_INSERTED = SynchedEntityData.defineId(GasCanisterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SYNC_APPEARANCE_READY = SynchedEntityData.defineId(GasCanisterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String DEFAULT_PROFILE = ModGasCanisters.STORAGE_ID.toString();
    private static final String PROFILE = "CanisterProfile";
    private static final String STATE = "CanisterState";
    private GasCanisterState canister;
    private GasCanisterState.Saved pending;
    private CompoundTag pendingTag;
    private String profileId = DEFAULT_PROFILE;
    private boolean invalid;
    private boolean removalCallbackActive;
    private boolean permanentRemovalPrepared;
    private int refillCadenceTicks;

    public static GasCanisterEntity create(Level level, Vec3 position, net.minecraft.resources.ResourceLocation profile) {
        GasCanisterEntity entity = new GasCanisterEntity(ModEntities.GAS_CANISTER.get(), level);
        entity.profileId = (profile == null ? ModGasCanisters.STORAGE_ID : profile).toString();
        entity.publishProfileId();
        entity.setPos(position);
        entity.setItem(new ItemStack(ModItems.GAS_CANISTER_PROXY.get()));
        entity.setDeltaMovement(Vec3.ZERO);
        return entity;
    }

    @Override public boolean admitsBodyHandPickup() { return false; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SYNC_PROFILE, DEFAULT_PROFILE);
        builder.define(SYNC_PRESSURE_BAND, 3);
        builder.define(SYNC_TANK_INSERTED, false);
        builder.define(SYNC_APPEARANCE_READY, false);
    }

    public record CanisterAppearance(String profileId, int pressureBand, boolean tankInserted,
                                     GasCanisterProfile profile) { }
    public Optional<CanisterAppearance> appearance() {
        if (!entityData.get(SYNC_APPEARANCE_READY)) return Optional.empty();
        String id = entityData.get(SYNC_PROFILE);
        try {
            GasCanisterProfile profile = ModGasCanisters.catalog(level()).get(net.minecraft.resources.ResourceLocation.parse(id));
            int band = entityData.get(SYNC_PRESSURE_BAND);
            if (profile == null || band < 0 || band > 3) return Optional.empty();
            return Optional.of(new CanisterAppearance(id, band, entityData.get(SYNC_TANK_INSERTED), profile));
        } catch (RuntimeException unavailable) { return Optional.empty(); }
    }

    public static int pressureBand(double kpa) {
        if (!Double.isFinite(kpa) || kpa < 0) return 0;
        return kpa < 10 ? 0 : kpa < 101.325 ? 1 : kpa < 1519.875 ? 2 : 3;
    }
    private void publishProfileId() { if (!level().isClientSide) entityData.set(SYNC_PROFILE, profileId); }
    private void updateAppearance(GasCanisterState state) {
        if (state == null) { entityData.set(SYNC_APPEARANCE_READY, false); return; }
        var slot = state.slot().snapshot();
        entityData.set(SYNC_PROFILE, profileId);
        entityData.set(SYNC_PRESSURE_BAND, pressureBand(state.gas().pressureKpa()));
        entityData.set(SYNC_TANK_INSERTED, !slot.stack().isEmpty() && slot.token() != null);
        entityData.set(SYNC_APPEARANCE_READY, true);
    }

    @Override public LooseItemEntity copy() {
        GasCanisterEntity copy = create(level(), position(), net.minecraft.resources.ResourceLocation.parse(profileId));
        copy.setDeltaMovement(getDeltaMovement());
        copy.setRotationAngle(getRotationAngle());
        return copy;
    }

    public GasCanisterEntity(EntityType<? extends GasCanisterEntity> type, Level level) {
        super(type, level);
        setItem(new ItemStack(ModItems.GAS_CANISTER_PROXY.get()));
    }

    public GasCanisterState canisterState() {
        // A retired entity may still be queried during unload/save. Never reactivate its
        // persisted owner claim; only a replacement entity may resume that ownership.
        if (isRemoved()) return canister;
        if (invalid || !(level() instanceof net.minecraft.server.level.ServerLevel server)
                || !server.getServer().isSameThread()) return canister;
        if (canister != null) {
            if (!canister.owner().equals(getUUID())) { invalid = true; canister = null; return null; }
            if (!canister.resume()) { invalid = true; return null; }
            return canister;
        }
        try {
            GasCanisterProfile profile = ModGasCanisters.catalog(level()).get(net.minecraft.resources.ResourceLocation.parse(profileId));
            if (profile == null) { invalid = true; return null; }
            var ledger = LevelItemLocationLedgers.forLevel(server);
            if (pending != null && !pending.owner().equals(getUUID())) { invalid = true; return null; }
            canister = pending == null
                    ? new GasCanisterState(net.minecraft.resources.ResourceLocation.parse(profileId), profile, getUUID(), ledger)
                    : GasCanisterState.restore(pending, profile, ledger);
            pending = null;
            pendingTag = null;
            if (!canister.owner().equals(getUUID()) || !canister.resume()) { canister = null; invalid = true; }
        } catch (RuntimeException failure) { invalid = true; canister = null; }
        return canister;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(PROFILE, profileId);
        GasCanisterState.Saved saved = canister == null ? pending : canister.snapshot();
        if (saved != null && level() instanceof net.minecraft.server.level.ServerLevel server) {
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var encoded = GasCanisterState.codec().encodeStart(ops, saved).result();
            if (encoded.isEmpty()) throw new IllegalStateException("Unable to encode gas canister state; refusing lossy save");
            tag.put(STATE, encoded.orElseThrow());
        } else if (pendingTag != null) tag.put(STATE, pendingTag.copy());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(PROFILE)) profileId = tag.getString(PROFILE);
        publishProfileId();
        canister = null;
        if (tag.contains(STATE) && level() instanceof net.minecraft.server.level.ServerLevel server) {
            pendingTag = tag.getCompound(STATE).copy();
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            pending = GasCanisterState.codec().parse(ops, pendingTag).result().orElse(null);
            if (pending == null) invalid = true;
        }
    }

    @Override public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        try {
            var profile = LooseObjectProfile.resolveForStack(getItem(), level().isClientSide);
            return net.minecraft.world.entity.EntityDimensions.scalable(profile.physicalWidth(), profile.physicalHeight());
        } catch (RuntimeException unavailable) { return super.getDimensions(pose); }
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) {
            GasCanisterState appearanceState = null;
            if (++refillCadenceTicks >= 20 && isAddedToLevel() && !isRemoved()
                    && level() instanceof net.minecraft.server.level.ServerLevel server
                    && server.getEntity(getUUID()) == this) {
                refillCadenceTicks = 0;
                GasCanisterState state = canisterState();
                appearanceState = state;
                if (state != null) {
                    var slotSnapshot = state.slot().snapshot();
                    long ledgerRevision = LevelItemLocationLedgers.forLevel(server).snapshot().revision();
                    state.refillFromInsertedTank(state.revision(), slotSnapshot.revision(), ledgerRevision);
                }
            }
            if (appearanceState == null) appearanceState = canisterState();
            updateAppearance(appearanceState);
            try {
                var profile = LooseObjectProfile.resolveForStack(getItem(), false);
                if (Float.compare(getBbWidth(), profile.physicalWidth()) != 0
                        || Float.compare(getBbHeight(), profile.physicalHeight()) != 0) refreshDimensions();
            } catch (RuntimeException catalogNotReady) { /* retry after catalogs become available */ }
        }
    }

    @Override public boolean beforeCanisterRemoval(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (removalCallbackActive) return false;
        if (permanentRemovalPrepared) return true;
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) return true;
        if (!server.getServer().isSameThread()) return false;
        removalCallbackActive = true;
        try {
            GasCanisterState state = canister;
            if (reason.shouldDestroy()) {
                // Never create an ownership claim for an entity that is not the live registered owner.
                if (state == null && (!isAddedToLevel() || isRemoved() || server.getEntity(getUUID()) != this)) {
                    if (pending != null || pendingTag != null) return false;
                    permanentRemovalPrepared = true;
                    return true;
                }
                if (state == null) state = canisterState();
                if (state == null) return pending == null && pendingTag == null;
                if (!state.owner().equals(getUUID())) return false;
                var snapshot = state.slot().snapshot();
                if (!snapshot.stack().isEmpty()) {
                    if (!isAddedToLevel() || isRemoved() || server.getEntity(getUUID()) != this) return false;
                    var ledger = LevelItemLocationLedgers.forLevel(server);
                    var result = com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.ejectToWorldOwned(
                            this, state.slot(), snapshot.revision(), ledger.snapshot().revision(), ledger,
                            () -> isAddedToLevel() && !isRemoved() && server.getEntity(getUUID()) == this);
                    if (result != com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS)
                        return false;
                }
                permanentRemovalPrepared = true;
                return true;
            }

            // Chunk/player unload and dimension changes retire this runtime claim while preserving
            // the serialized slot payload. Normal later state access resumes the same owner.
            if (canister != null) {
                if (!canister.suspend()) return false;
                pending = canister.snapshot();
                canister = null;
            }
            return true;
        } finally {
            removalCallbackActive = false;
        }
    }

}

