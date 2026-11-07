package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.hands.transfer.LevelItemLocationLedgers;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Owns one persistent, ledger-claimed charger slot and its item charge state. */
public final class WeaponCapacitorRechargerBlockEntity extends BlockEntity {
    private static final String ID = "PlacementIdentity", SLOT = "ChargerSlot";
    private UUID identity = UUID.randomUUID();
    private MachineItemSlot slot;
    private MachineItemSlot.EncodedData pending;
    private long lastPoweredTick = Long.MIN_VALUE;
    private boolean removalDrainFailed;
    public WeaponCapacitorRechargerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.WEAPON_CAPACITOR_RECHARGER.get(), pos, state); }
    public UUID placementIdentity() { return identity; }
    /** Read-only native-fixture evidence for the most recent full-power admitted server tick. */
    long lastAuthorizedAdvanceTick() { return lastPoweredTick; }
    public boolean hasResolvedChargerProfile() {
        return ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger")) != null;
    }
    public double demandWatts() {
        if (!ensureSlot()) return 0;
        var profile = ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
        if (profile == null) return 0;
        var snap = slot.snapshot();
        var target = snap.stack().isEmpty() ? null : ChargerBatteryResolver.resolve(snap.stack(), ModGunProfiles.serverCatalog().asMap(), ModBatteryProfiles.serverCatalog().asMap()).orElse(null);
        float charge = target == null ? 0 : target.state() == null ? target.profile().startingCharge() : target.state().charge();
        return target != null && charge < target.profile().capacity() ? profile.chargeRate() : profile.idlePower();
    }
    public boolean prepareInsertedCharge() {
        if (!ensureSlot()) return false;
        var snap = slot.snapshot(); if (snap.stack().isEmpty() || snap.token() == null) return true;
        var profile = ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
        var prepared = ChargerBatteryResolver.prepareAcceptedStack(snap.stack(), profile, ModGunProfiles.serverCatalog().asMap(), ModBatteryProfiles.serverCatalog().asMap()).orElse(null);
        if (prepared == null) return false;
        boolean initialized = slot.mutate(snap.revision(), snap.token(), stack -> { stack.applyComponents(prepared.getComponents()); return true; });
        if (initialized && level instanceof ServerLevel server) com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition);
        return initialized;
    }
    public boolean isLoadedValid() { return level instanceof ServerLevel && !isRemoved() && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this; }
    public synchronized MachineItemSlot slot() { return ensureSlot() ? slot : null; }
    private boolean ensureSlot() {
        if (!(level instanceof ServerLevel server)) return false;
        if (slot != null) return !slot.isRetired() || slot.resume();
        {
            var charger = ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
            if (charger == null) return false;
            var guns = ModGunProfiles.serverCatalog().asMap(); var batteries = ModBatteryProfiles.serverCatalog().asMap();
            ItemLocationLedger ledger = LevelItemLocationLedgers.forLevel(server);
            slot = new MachineItemSlot(identity, charger.slotId(), stack -> ChargerBatteryResolver.resolve(stack, guns, batteries)
                    .filter(target -> ChargerBatteryResolver.accepted(stack, target, charger, guns)).isPresent(), ledger);
            if (pending != null && !slot.restore(pending)) return false;
            pending = null;
        }
        return true;
    }
    public void tick() {
        if (!(level instanceof ServerLevel server) || !isLoadedValid() || !ensureSlot()) return;
        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.tickCharger(server, this);
        var observation = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMeter(server, this);
        boolean powered = observation.known() && observation.deliveredWatts() == demandWatts();
        var snapshot = slot.snapshot();
        var target = snapshot.stack().isEmpty() ? null : ChargerBatteryResolver.resolve(snapshot.stack(), ModGunProfiles.serverCatalog().asMap(), ModBatteryProfiles.serverCatalog().asMap()).orElse(null);
        float targetCharge = target == null ? 0 : target.state() == null
                ? target.profile().startingCharge() : target.state().charge();
        WeaponCapacitorRechargerBlock.Visual visual = WeaponCapacitorRechargerBlock.projectVisual(
                powered, target != null, targetCharge, target == null ? 0 : target.profile().capacity());
        BlockState state = getBlockState();
        if (state.hasProperty(WeaponCapacitorRechargerBlock.VISUAL) && state.getValue(WeaponCapacitorRechargerBlock.VISUAL) != visual)
            server.setBlock(worldPosition, state.setValue(WeaponCapacitorRechargerBlock.VISUAL, visual), 3);
    }
    public void advanceOnePoweredTick() {
        if (!(level instanceof ServerLevel server) || !isLoadedValid() || !ensureSlot()) return;
        long now = server.getServer().getTickCount();
        if (lastPoweredTick == now) return;
        lastPoweredTick = now;
        var snap = slot.snapshot(); if (snap.stack().isEmpty() || snap.token() == null) return;
        var charger = ModChargerProfiles.serverCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_capacitor_recharger"));
        var target = ChargerBatteryResolver.resolve(snap.stack(), ModGunProfiles.serverCatalog().asMap(), ModBatteryProfiles.serverCatalog().asMap()).orElse(null);
        if (charger == null || target == null || target.state() == null) return;
        var result = ChargerKernel.charge(target.state(), target.profile(), charger, 1d / 20d, true);
        if (!result.accepted()) return;
        float beforeCharge = target.state().charge();
        boolean committed = slot.mutate(snap.revision(), snap.token(), stack -> {
            var live = ChargerBatteryResolver.resolve(stack, ModGunProfiles.serverCatalog().asMap(), ModBatteryProfiles.serverCatalog().asMap()).orElse(null);
            if (live == null || live.state() == null) return false;
            ItemStackMutation.replace(stack, ChargerBatteryResolver.writeBatteryCharge(stack, live, result.charge()));
            return true;
        });
        if (committed && result.charge() >= target.profile().capacity() && beforeCharge < target.profile().capacity())
            com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition);
        setChanged();
    }
    public void slotChanged() { if (level instanceof ServerLevel server) { com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition); setChanged(); } }
    /** Called only for actual block replacement/removal, not chunk unload. */
    public boolean dropInstalledItems() {
        if (!(level instanceof ServerLevel server) || !isLoadedValid() || !ensureSlot()) return false;
        var snapshot = slot.snapshot(); if (snapshot.stack().isEmpty()) return true;
        var ledger = LevelItemLocationLedgers.forLevel(server);
        var result = com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.ejectToWorldOwnedAt(
                server, worldPosition, identity, slot, snapshot.revision(), ledger.snapshot().revision(), ledger, this::isLoadedValid);
        if (result == com.juicyslew.moonstation14.ms14.hands.live.BodyMachineItemSlotTransfer.Result.SUCCESS) { setChanged(); return true; }
        removalDrainFailed = true;
        return false;
    }
    private static final class ItemStackMutation {
        static void replace(net.minecraft.world.item.ItemStack target, net.minecraft.world.item.ItemStack source) {
            target.applyComponents(source.getComponents());
        }
    }
    @Override public void onLoad() { super.onLoad(); if (level instanceof ServerLevel server) { ensureSlot(); com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineChanged(server, worldPosition); } }
    @Override public void setRemoved() { if (level instanceof ServerLevel server) { if (slot != null && !removalDrainFailed) slot.suspend(); com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.processingMachineRemoved(server, worldPosition); } super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.putUUID(ID, identity);
        if (ensureSlot() && slot != null) MachineItemSlot.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), slot.encode()).result().ifPresent(v -> tag.put(SLOT, v));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); if (tag.hasUUID(ID)) identity = tag.getUUID(ID);
        if (tag.contains(SLOT)) pending = MachineItemSlot.codec().parse(
                registries.createSerializationContext(NbtOps.INSTANCE), tag.get(SLOT)).result().orElse(null);
    }
}
