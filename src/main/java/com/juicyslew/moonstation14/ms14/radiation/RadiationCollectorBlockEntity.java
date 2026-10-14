package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.Connection;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative radiation collector power-output state. */
public final class RadiationCollectorBlockEntity extends BlockEntity implements PowerRuntimeNode {
    private static final String ID = "identity";
    private UUID identity = UUID.randomUUID();
    private UUID slotOwner = UUID.randomUUID();
    private MachineItemSlot tank;
    private CompoundTag pendingTank;
    private boolean enabled;
    private boolean initialized;
    private ResourceLocation profileId = ModRadiationCollectors.EMPTY_ID;
    private int holdover;
    private long nextToggleTime;
    private double currentWatts;
    private double ratedWatts;
    private double outputWatts;
    private CollectorVisualSnapshot clientVisual = CollectorVisualSnapshot.empty();
    private CollectorVisualSnapshot lastPublishedVisual;
    private long visualTransitionStartedAt;
    private boolean visualTransitioning;
    private boolean visualTransitionTargetEnabled;

    public record CollectorVisualSnapshot(boolean enabled, boolean tankPresent, int pressureBand,
                                          ResourceLocation profileId, boolean transitioning,
                                          boolean transitionTargetEnabled, long transitionStartedAt) {
        public static CollectorVisualSnapshot empty() {
            return new CollectorVisualSnapshot(false, false, 0, ModRadiationCollectors.EMPTY_ID, false, false, 0);
        }
    }

    public RadiationCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADIATION_COLLECTOR.get(), pos, state);
    }

    public UUID identity() { return identity; }
    public ResourceLocation profileId() { return profileId; }
    public CollectorVisualSnapshot visualSnapshot() {
        return level instanceof ServerLevel ? deriveVisualSnapshot() : clientVisual;
    }
    public boolean controlStateReady() { return ensureState() && resolvedProfile() != null; }

    /** A block drop carries machine configuration, never its privately owned installed tank. */
    public CompoundTag saveForItem(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = saveWithFullMetadata(registries);
        tag.remove("tankSlot");
        tag.putBoolean("initialized", true);
        return tag;
    }

    public void permanentlyRemove() {
        if (level instanceof ServerLevel server) {
            RadiationService.INSTANCE.removeReceiver(server, identity);
            PowerRuntime.deviceRemoved(server, worldPosition);
        }
        if (tank != null) tank.dispose();
    }

    @Override public List<DevicePort> powerRuntimePorts() {
        return List.of(new DevicePort(worldPosition, Direction.NORTH, CableTier.HV));
    }

    @Override public PowerRuntimeNode.SupplierOffer powerSupplierOffer() {
        return new PowerRuntimeNode.SupplierOffer(outputWatts, outputWatts);
    }
    @Override public void receiveMvPowerAllocation(double actualWatts, boolean graphKnown, long sampleTick) { }

    /** Irradiation callback publishes the computed output as both actual and rated capacity. */
    public void receiveIrradiation(double watts, int configuredHoldover) {
        if (!(level instanceof ServerLevel) || !Double.isFinite(watts) || watts < 0) return;
        currentWatts = watts;
        ratedWatts = watts;
        holdover = Math.max(0, configuredHoldover);
        outputWatts = currentWatts;
        setChanged();
        // Offers are read on the normal solve cadence; changing this value does not
        // alter the collector's identity or ports, so it must not invalidate topology.
    }

    public MachineItemSlot tankSlot() { ensureState(); return tank; }
    public boolean enabled() { return enabled; }
    public boolean toggleEnabled() {
        if (!(level instanceof ServerLevel server) || !ensureState()) return false;
        RadiationCollectorProfile profile = resolvedProfile();
        if (profile == null || server.getGameTime() < nextToggleTime) return false;
        enabled = !enabled;
        applyMotion(RadiationCollectorMotion.toggle(motionState(), enabled, server.getGameTime()));
        setChanged(); publishVisualIfChanged();
        nextToggleTime = server.getGameTime() + profile.useDelayTicks();
        if (!enabled) RadiationService.INSTANCE.removeReceiver(server, identity);
        else registerReceiver(server);
        return true;
    }
    private boolean ensureState() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return false;
        if (tank == null) tank = new MachineItemSlot(slotOwner, "tankSlot", stack -> GasTankItem.contents(stack, server).isPresent(),
                com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers.forLevel(server));
        else if (!tank.resume()) return false;
        if (pendingTank != null) {
            var decoded = MachineItemSlot.codec().parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), pendingTank).result();
            if (decoded.isEmpty() || !tank.restore(decoded.get())) return false;
            pendingTank = null;
        }
        if (!initialized) {
            RadiationCollectorProfile profile = resolvedProfile();
            if (profile == null) return false;
            enabled = profile.enabled();
            if (!profile.startingTank().isBlank()) {
                var contents = GasTankItem.createContents(com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks.catalog(server),
                        net.minecraft.resources.ResourceLocation.parse(profile.startingTank()));
                ItemStack stack = new ItemStack(ModItems.PLASMA_TANK.get());
                stack.set(com.juicyslew.moonstation14.component.ModDataComponents.GAS_TANK_CONTENTS.get(), contents);
                if (!tank.initialize(0, stack)) return false;
            }
            initialized = true;
            setChanged();
            publishVisualIfChanged();
        }
        return true;
    }
    private RadiationCollectorProfile resolvedProfile() {
        return ModRadiationCollectors.catalog(level).get(profileId);
    }
    /** Select a typed profile before fresh initialization (e.g. block-item entity data). */
    public boolean selectProfile(ResourceLocation next) {
        if (initialized || next == null || ModRadiationCollectors.catalog(level).get(next) == null) return false;
        profileId = next; setChanged(); return true;
    }
    private void registerReceiver(ServerLevel server) {
        RadiationCollectorProfile profile = resolvedProfile();
        if (profile == null || !enabled) return;
        RadiationService.INSTANCE.registerReceiver(server, identity, worldPosition, (dose, elapsed) -> {
            processRadiationDose(dose, elapsed);
        });
    }

    /** The receiver's production consumer, also directly exercisable by native fixtures. */
    public boolean processRadiationDose(double dosePerSecond, double elapsedSeconds) {
        if (!(level instanceof ServerLevel server) || !enabled || !ensureState() || tank == null) return false;
        RadiationCollectorProfile profile = resolvedProfile();
        if (profile == null) return false;
        MachineItemSlot.Snapshot snapshot = tank.snapshot();
        GasTankContents contents = GasTankItem.contents(snapshot.stack(), server).orElse(null);
        if (contents == null || snapshot.token() == null) return false;
        var result = RadiationCollectorReaction.react(contents.mixture(), profile.reactant(), dosePerSecond * elapsedSeconds,
                profile.chargeModifier(), profile.efficiency(), profile.breakdownRate());
        if (result.plasmaLossMoles() > 0 && !tank.mutate(snapshot.revision(), snapshot.token(), stack -> {
            GasTankContents current = GasTankItem.contents(stack, server).orElse(null);
            if (current == null) return false;
            stack.set(com.juicyslew.moonstation14.component.ModDataComponents.GAS_TANK_CONTENTS.get(), current.withMixture(result.remaining()));
            return true;
        })) return false;
        receiveIrradiation(result.powerWatts(), profile.powerHoldoverTicks());
        return true;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RadiationCollectorBlockEntity entity) {
        if (entity.isRemoved() || entity.level != level) return;
        if (!entity.initialized && entity.ensureState() && entity.enabled) entity.registerReceiver(level);
        entity.applyMotion(RadiationCollectorMotion.advance(entity.motionState(), entity.enabled, level.getGameTime()));
        entity.publishVisualIfChanged();
        if (entity.outputWatts <= 0) return;
        if (entity.holdover > 0) entity.holdover--;
        else {
            entity.outputWatts = 0;
            entity.currentWatts = 0;
            entity.ratedWatts = 0;
            entity.setChanged();
            // Holdover expiry only changes the dynamic offer; the next scheduled solve observes zero.
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) { ensureState(); publishVisualIfChanged(); }
        if (level instanceof ServerLevel server) PowerRuntime.deviceChanged(server, worldPosition);
    }

    @Override public void clearRemoved() { super.clearRemoved(); if (tank != null && !tank.resume()) setChanged(); }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) { RadiationService.INSTANCE.removeReceiver(server, identity); PowerRuntime.deviceRemoved(server, worldPosition); }
        if (tank != null) tank.suspend();
        super.setRemoved();
    }

    @Override protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(ID, identity);
        tag.putString("profile", profileId.toString());
        tag.putUUID("slotOwner", slotOwner); tag.putBoolean("enabled", enabled); tag.putBoolean("initialized", initialized);
        if (pendingTank != null) tag.put("tankSlot", pendingTank.copy());
        else if (tank != null) MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), tank.encode()).result().ifPresent(value -> tag.put("tankSlot", value));
        tag.putInt("holdover", holdover);
        tag.putLong("nextToggleTime", nextToggleTime);
        tag.putDouble("currentWatts", currentWatts);
        tag.putDouble("ratedWatts", ratedWatts);
        tag.putDouble("outputWatts", outputWatts);
        tag.putLong("visualTransitionStartedAt", visualTransitionStartedAt);
        tag.putBoolean("visualTransitioning", visualTransitioning);
        tag.putBoolean("visualTransitionTargetEnabled", visualTransitionTargetEnabled);
    }

    @Override protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(ID)) identity = tag.getUUID(ID);
        ResourceLocation storedProfile = ResourceLocation.tryParse(tag.getString("profile"));
        if (storedProfile != null) profileId = storedProfile;
        if (tag.hasUUID("slotOwner")) slotOwner = tag.getUUID("slotOwner");
        enabled = tag.getBoolean("enabled"); initialized = tag.getBoolean("initialized");
        pendingTank = tag.contains("tankSlot") ? tag.getCompound("tankSlot") : null; tank = null;
        holdover = Math.max(0, tag.getInt("holdover"));
        nextToggleTime = Math.max(0, tag.getLong("nextToggleTime"));
        currentWatts = finiteNonNegative(tag.getDouble("currentWatts"));
        ratedWatts = finiteNonNegative(tag.getDouble("ratedWatts"));
        outputWatts = Math.min(finiteNonNegative(tag.getDouble("outputWatts")), ratedWatts);
        visualTransitionStartedAt = Math.max(0, tag.getLong("visualTransitionStartedAt"));
        visualTransitioning = tag.getBoolean("visualTransitioning");
        visualTransitionTargetEnabled = tag.getBoolean("visualTransitionTargetEnabled");
    }

    private CollectorVisualSnapshot deriveVisualSnapshot() {
        if (!(level instanceof ServerLevel server)) return clientVisual;
        boolean present = false;
        int band = 0;
        if (ensureState() && tank != null) {
            var contents = GasTankItem.contents(tank.snapshot().stack(), server).orElse(null);
            if (contents != null) {
                present = true;
                double kpa = contents.mixture().pressureKpa();
                band = RadiationCollectorVisualAppearance.pressureBand(true, kpa);
            }
        }
        ResourceLocation resolvedId = resolvedProfile() == null ? ModRadiationCollectors.EMPTY_ID : profileId;
        return new CollectorVisualSnapshot(enabled, present, band, resolvedId, visualTransitioning,
                visualTransitionTargetEnabled, visualTransitionStartedAt);
    }

    private RadiationCollectorMotion.State motionState() {
        return new RadiationCollectorMotion.State(visualTransitioning, visualTransitionTargetEnabled, visualTransitionStartedAt);
    }
    private void applyMotion(RadiationCollectorMotion.State state) {
        visualTransitioning = state.transitioning();
        visualTransitionTargetEnabled = state.clipTargetEnabled();
        visualTransitionStartedAt = state.startedAt();
    }

    private void publishVisualIfChanged() {
        if (!(level instanceof ServerLevel)) return;
        CollectorVisualSnapshot snapshot = deriveVisualSnapshot();
        if (snapshot.equals(lastPublishedVisual)) return;
        lastPublishedVisual = snapshot;
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, 3);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CollectorVisualSnapshot view = level instanceof ServerLevel ? deriveVisualSnapshot() : clientVisual;
        return encodeVisualSnapshot(view);
    }
    @Override public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        clientVisual = decodeVisualSnapshot(tag);
    }
    public static CompoundTag encodeVisualSnapshot(CollectorVisualSnapshot view) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("VisualEnabled", view.enabled()); tag.putBoolean("VisualTankPresent", view.tankPresent());
        tag.putInt("VisualPressureBand", view.pressureBand()); tag.putString("VisualProfile", view.profileId().toString());
        tag.putBoolean("VisualTransitioning", view.transitioning()); tag.putBoolean("VisualTransitionTarget", view.transitionTargetEnabled());
        tag.putLong("VisualTransitionStartedAt", view.transitionStartedAt());
        return tag;
    }
    public static CollectorVisualSnapshot decodeVisualSnapshot(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("VisualProfile"));
        return new CollectorVisualSnapshot(tag.getBoolean("VisualEnabled"), tag.getBoolean("VisualTankPresent"),
                Math.max(0, Math.min(3, tag.getInt("VisualPressureBand"))), id == null ? ModRadiationCollectors.EMPTY_ID : id,
                tag.getBoolean("VisualTransitioning"), tag.getBoolean("VisualTransitionTarget"),
                Math.max(0, tag.getLong("VisualTransitionStartedAt")));
    }
    @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                                       net.minecraft.core.HolderLookup.Provider registries) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag(), registries);
    }

    private static double finiteNonNegative(double value) { return Double.isFinite(value) ? Math.max(0, value) : 0; }
}

