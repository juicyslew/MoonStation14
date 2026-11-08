package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure provider state transitions; materialization and projectile publication belong to live owners. */
public final class RevolverProviderKernel {
    private RevolverProviderKernel() {}
    public static RevolverState initialize(GunProfile profile) {
        var provider = profile.revolverAmmoProvider().orElseThrow();
        if (provider.capacity() > RevolverState.MAX_CAPACITY) throw new IllegalArgumentException("provider capacity exceeds limit");
        return new RevolverState(0, java.util.Collections.nCopies(provider.capacity(), new RevolverState.Chamber(true, Optional.empty())));
    }
    public static RevolverState cycle(RevolverState state) {
        return new RevolverState((state.index() + 1) % state.chambers().size(), state.chambers());
    }
    public static int occupiedCount(RevolverState state) {
        return (int) state.chambers().stream().filter(c -> c.virtualLive() || c.physical().isPresent() || c.spentCasing().isPresent()).count();
    }
    public static int unspentCount(RevolverState state) {
        return (int) state.chambers().stream().filter(c -> c.virtualLive() || c.physical().filter(s -> !Boolean.TRUE.equals(s.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))).isPresent()).count();
    }
    public static InsertResult insertOne(RevolverState state, GunProfile gun, net.minecraft.world.item.ItemStack cartridgeStack,
                                         Map<ResourceLocation, CartridgeProfile> catalog) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cartridgeStack.getItem());
        CartridgeProfile profile = catalog.get(id);
        if (profile == null) return new InsertResult(false, -1, state, InsertFailure.UNKNOWN_CARTRIDGE);
        if (profile.tags().stream().noneMatch(gun.revolverAmmoProvider().orElseThrow().acceptedCartridgeTags()::contains))
            return new InsertResult(false, -1, state, InsertFailure.WRONG_TAG);
        int size = state.chambers().size();
        for (int offset = 0; offset < size; offset++) {
            int slot = (state.index() + offset) % size;
            var chamber = state.chambers().get(slot);
            if (!chamber.virtualLive() && chamber.physical().isEmpty() && chamber.spentCasing().isEmpty()) {
                var updated = new ArrayList<>(state.chambers());
                updated.set(slot, new RevolverState.Chamber(false, Optional.of(cartridgeStack.copy())));
                return new InsertResult(true, slot, new RevolverState(state.index(), updated), null);
            }
        }
        return new InsertResult(false, -1, state, InsertFailure.FULL);
    }
    public static EjectResult ejectAll(RevolverState state, GunProfile gun) {
        var outputs = new ArrayList<Ejection>();
        var cleared = new ArrayList<RevolverState.Chamber>();
        for (var chamber : state.chambers()) {
            if (chamber.physical().isPresent()) outputs.add(new Ejection(Optional.of(chamber.physical().orElseThrow()), Optional.empty(),
                    Boolean.TRUE.equals(chamber.physical().orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))));
            else if (chamber.spentCasing().isPresent()) outputs.add(new Ejection(Optional.empty(), chamber.spentCasing(), true));
            else if (chamber.virtualLive()) outputs.add(new Ejection(Optional.empty(), Optional.of(gun.revolverAmmoProvider().orElseThrow().fillCartridgePrototype()), false));
            cleared.add(RevolverState.Chamber.empty());
        }
        return new EjectResult(new RevolverState(state.index(), cleared), outputs);
    }
    public enum InsertFailure { UNKNOWN_CARTRIDGE, WRONG_TAG, FULL }
    public record InsertResult(boolean inserted, int chamberIndex, RevolverState state, InsertFailure failure) {}
    public record Ejection(Optional<net.minecraft.world.item.ItemStack> physical, Optional<ResourceLocation> deferredPrototype, boolean spent) {
        public Ejection { physical = physical == null ? Optional.empty() : physical.map(net.minecraft.world.item.ItemStack::copy); }
        @Override public Optional<net.minecraft.world.item.ItemStack> physical() { return physical.map(net.minecraft.world.item.ItemStack::copy); }
    }
    public record EjectResult(RevolverState state, List<Ejection> ejected) { public EjectResult { ejected = List.copyOf(ejected); } }
    public static TakeResult takeOne(RevolverState state, GunProfile gun, Map<ResourceLocation, CartridgeProfile> cartridges,
                                     Map<ResourceLocation, ProjectileProfile> projectiles) {
        int index = state.index();
        var chamber = state.chambers().get(index);
        var changed = new ArrayList<>(state.chambers());
        if (chamber.physical().isPresent()) {
            var physical = chamber.physical().orElseThrow();
            var spent = Boolean.TRUE.equals(physical.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
            changed.set(index, chamber);
            var next = new RevolverState((index + 1) % changed.size(), changed);
            if (spent) return new TakeResult(next, TakeStatus.EMPTY, Optional.empty());
            return physicalShot(next, index, physical, cartridges, projectiles);
        }
        changed.set(index, chamber.spentCasing().isPresent() ? chamber : RevolverState.Chamber.empty());
        var next = new RevolverState((index + 1) % changed.size(), changed);
        if (!chamber.virtualLive()) return new TakeResult(next, TakeStatus.EMPTY, Optional.empty());
        ResourceLocation cartridgeId = gun.revolverAmmoProvider().orElseThrow().fillCartridgePrototype();
        CartridgeProfile cartridge = cartridges.get(cartridgeId);
        if (cartridge == null) return new TakeResult(state, TakeStatus.INVALID_PROFILE, Optional.empty());
        ProjectileProfile projectile = projectiles.get(cartridge.projectile());
        if (projectile == null) return new TakeResult(state, TakeStatus.INVALID_PROFILE, Optional.empty());
        changed.set(index, cartridge.deleteOnSpawn() ? RevolverState.Chamber.empty() : RevolverState.Chamber.spentDeferred(cartridgeId));
        next = new RevolverState(next.index(), changed);
        return new TakeResult(next, TakeStatus.READY, Optional.of(new ShotIntent(index, Optional.empty(), cartridgeId, cartridge.projectile(), cartridge.deleteOnSpawn(), projectile)));
    }
    private static TakeResult physicalShot(RevolverState next, int index, net.minecraft.world.item.ItemStack stack,
            Map<ResourceLocation, CartridgeProfile> cartridges, Map<ResourceLocation, ProjectileProfile> projectiles) {
        ResourceLocation cartridgeId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        CartridgeProfile cartridge = cartridges.get(cartridgeId);
        if (cartridge == null) return new TakeResult(next, TakeStatus.INVALID_PROFILE, Optional.empty());
        ProjectileProfile projectile = projectiles.get(cartridge.projectile());
        if (projectile == null) return new TakeResult(next, TakeStatus.INVALID_PROFILE, Optional.empty());
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var updated = new ArrayList<>(next.chambers());
        updated.set(index, cartridge.deleteOnSpawn() ? RevolverState.Chamber.empty() : new RevolverState.Chamber(false, Optional.of(stack.copy())));
        next = new RevolverState(next.index(), updated);
        return new TakeResult(next, TakeStatus.READY, Optional.of(new ShotIntent(index, Optional.of(stack.copy()), cartridgeId, cartridge.projectile(), cartridge.deleteOnSpawn(), projectile)));
    }
    public enum TakeStatus { READY, EMPTY, INVALID_PROFILE }
    public record TakeResult(RevolverState state, TakeStatus status, Optional<ShotIntent> shot) {}
    public record ShotIntent(int chamberIndex, Optional<net.minecraft.world.item.ItemStack> physical,
                             ResourceLocation cartridgePrototype, ResourceLocation projectilePrototype,
                             boolean deleteOnSpawn, ProjectileProfile projectile) {
        public ShotIntent { physical = physical == null ? Optional.empty() : physical.map(net.minecraft.world.item.ItemStack::copy); }
        @Override public Optional<net.minecraft.world.item.ItemStack> physical() { return physical.map(net.minecraft.world.item.ItemStack::copy); }
    }
}
