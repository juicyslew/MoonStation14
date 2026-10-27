package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RevolverProviderTest {
    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("moonstation14", name); }
    @Test void virtualRoundsFireAdvanceAndRetainCasings() {
        var manager = new PrototypeManager(); manager.register(ModGunProfiles.TYPE);
        var resources = com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.resources("gun_profile", "base_weapon_revolver", "weapon_revolver_python", "base_toy_gun", "revolver_cap_gun", "revolver_cap_gun_fake");
        Map<ResourceLocation,GunProfile> guns = new LinkedHashMap<>();
        manager.stage(Map.of(ModGunProfiles.TYPE, resources), catalogs -> catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key, raw) -> guns.put(key, GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow())));
        var cartridges = Map.of(id("cartridge_magnum"), new CartridgeProfile(id("bullet_magnum"), false, Set.of()),
                id("cartridge_magnum_ap"), new CartridgeProfile(id("bullet_magnum_ap"), false, Set.of()),
                id("cap"), new CartridgeProfile(id("bullet_magnum"), true, Set.of()));
        var projectiles = Map.of(id("bullet_magnum"), new ProjectileProfile(Map.of(), false, true, 10),
                id("bullet_magnum_ap"), new ProjectileProfile(Map.of("piercing", 5f), false, true, 10));
        var gun = guns.get(id("weapon_revolver_python"));
        var state = RevolverProviderKernel.initialize(gun); assertEquals(6, state.chambers().size());
        for (int n=0;n<6;n++) {
            var result = RevolverProviderKernel.takeOne(state, gun, cartridges, projectiles); state=result.state();
            assertTrue(result.shot().isPresent()); assertEquals(id("bullet_magnum"), result.shot().orElseThrow().projectilePrototype());
            if(n==5) assertTrue(state.chambers().stream().allMatch(c -> c.spentCasing().isPresent()));
        }
        assertTrue(RevolverProviderKernel.takeOne(state, gun, cartridges, projectiles).shot().isEmpty());
        assertTrue(RevolverProviderKernel.takeOne(RevolverState.empty(6), gun, cartridges, projectiles).shot().isEmpty());
        var ap = RevolverProviderKernel.initialize(guns.get(id("revolver_cap_gun_fake")));
        var apShot = RevolverProviderKernel.takeOne(ap, guns.get(id("revolver_cap_gun_fake")), cartridges, projectiles).shot().orElseThrow();
        assertEquals(id("bullet_magnum_ap"), apShot.projectilePrototype());
        assertTrue(RevolverProviderKernel.cycle(RevolverProviderKernel.cycle(state)).index() == 2);
    }

    @Test void stateRejectsImpossibleChambersAndPreservesEmptyLoadedState() {
        assertThrows(IllegalArgumentException.class, () -> new RevolverState(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RevolverState.Chamber(true, Optional.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK))));
        var unloaded = RevolverState.empty(6);
        assertEquals(RevolverProviderKernel.TakeStatus.EMPTY, RevolverProviderKernel.takeOne(unloaded, dummyGun(), Map.of(), Map.of()).status());
        assertEquals(1, RevolverProviderKernel.cycle(unloaded).index());
    }

    @Test void virtualRoundWithMissingCatalogIsRejectedWithoutConsuming() {
        var gun = dummyGun();
        var original = RevolverProviderKernel.initialize(gun);
        var denied = RevolverProviderKernel.takeOne(original, gun, Map.of(), Map.of());
        assertEquals(RevolverProviderKernel.TakeStatus.INVALID_PROFILE, denied.status()); assertEquals(original, denied.state());
    }

    private static GunProfile dummyGun() {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(6, id("cartridge_magnum"), Set.of(), Set.of())));
    }

    @Test void physicalLiveAndSpentRoundsUseCopiesAndDeletePolicy() {
        var stickId = ResourceLocation.withDefaultNamespace("stick");
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(stickId);
        var stack = new net.minecraft.world.item.ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("marked round"));
        var live = new RevolverState(0, List.of(new RevolverState.Chamber(false, Optional.of(stack))));
        var catalogs = Map.of(stickId, new CartridgeProfile(id("bullet_magnum"), false, Set.of()));
        var projectiles = Map.of(id("bullet_magnum"), new ProjectileProfile(Map.of(), false, true, 10));
        var take = RevolverProviderKernel.takeOne(live, dummyGun(), catalogs, projectiles);
        assertEquals(RevolverProviderKernel.TakeStatus.READY, take.status());
        assertFalse(stack.has(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        var retained = take.state().chambers().get(0).physical().orElseThrow();
        assertEquals(true, retained.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        assertEquals(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), retained.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        take.shot().orElseThrow().physical().orElseThrow().setCount(9);
        assertEquals(1, take.state().chambers().get(0).physical().orElseThrow().getCount());
        var spent = RevolverProviderKernel.takeOne(take.state(), dummyGun(), catalogs, projectiles);
        assertEquals(RevolverProviderKernel.TakeStatus.EMPTY, spent.status()); assertEquals(0, spent.state().index());

        var deleting = Map.of(stickId, new CartridgeProfile(id("bullet_magnum"), true, Set.of()));
        var deleted = RevolverProviderKernel.takeOne(live, dummyGun(), deleting, projectiles);
        assertTrue(deleted.shot().isPresent()); assertTrue(deleted.state().chambers().get(0).physical().isEmpty());
        assertEquals(1, stack.getCount());
    }

    @Test void revolverStateCodecAndStreamPreserveMixedOwnedStacks() {
        var stick = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stick")));
        stick.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("codec round"));
        var state = new RevolverState(3, List.of(new RevolverState.Chamber(true, Optional.empty()), RevolverState.Chamber.empty(),
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")), new RevolverState.Chamber(false, Optional.of(stick)),
                RevolverState.Chamber.empty(), RevolverState.Chamber.empty()));
        var json = RevolverState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, RevolverState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, state).getOrThrow()).getOrThrow();
        assertEquals(state.index(), json.index()); assertEquals(state.chambers().size(), json.chambers().size());
        assertEquals(state.chambers().get(2).spentCasing(), json.chambers().get(2).spentCasing());
        assertEquals(state.chambers().get(3).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), json.chambers().get(3).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        RevolverState decoded;
        try { RevolverState.STREAM_CODEC.encode(buffer, state); decoded = RevolverState.STREAM_CODEC.decode(buffer); }
        finally { buffer.release(); }
        assertEquals(3, decoded.index()); assertEquals(state.chambers().size(), decoded.chambers().size());
        assertTrue(decoded.chambers().get(0).virtualLive()); assertEquals(state.chambers().get(2).spentCasing(), decoded.chambers().get(2).spentCasing());
        assertEquals(state.chambers().get(3).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), decoded.chambers().get(3).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        decoded.chambers().get(3).physical().orElseThrow().setCount(7);
        assertEquals(1, decoded.chambers().get(3).physical().orElseThrow().getCount());
    }

    @Test void insertUsesTagsAndFirstFreeFromCurrentIndexIncludingSpentCasings() {
        var providerGun = gunWithTags(Set.of(id("cap")));
        var state = new RevolverState(4, List.of(RevolverState.Chamber.empty(), RevolverState.Chamber.empty(),
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")), RevolverState.Chamber.empty(),
                new RevolverState.Chamber(true, Optional.empty()), RevolverState.Chamber.empty()));
        var stick = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stick")));
        stick.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("ammo label"));
        var capCatalog = Map.of(ResourceLocation.withDefaultNamespace("stick"), new CartridgeProfile(id("bullet_magnum"), false, Set.of(id("cap"))));
        var inserted = RevolverProviderKernel.insertOne(state, providerGun, stick, capCatalog);
        assertTrue(inserted.inserted()); assertEquals(5, inserted.chamberIndex()); assertEquals(4, inserted.state().index());
        assertEquals(stick.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), inserted.state().chambers().get(5).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        var spentStick = stick.copy(); spentStick.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var withSpent = RevolverProviderKernel.insertOne(state, providerGun, spentStick, capCatalog);
        assertTrue(Boolean.TRUE.equals(withSpent.state().chambers().get(5).physical().orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        var wrong = RevolverProviderKernel.insertOne(state, gunWithTags(Set.of(id("magnum"))), stick, capCatalog);
        assertFalse(wrong.inserted()); assertSame(state, wrong.state());
        var full = new RevolverState(4, List.of(new RevolverState.Chamber(true, Optional.empty()), new RevolverState.Chamber(true, Optional.empty()),
                new RevolverState.Chamber(true, Optional.empty()), new RevolverState.Chamber(true, Optional.empty()), new RevolverState.Chamber(true, Optional.empty()), new RevolverState.Chamber(true, Optional.empty())));
        assertFalse(RevolverProviderKernel.insertOne(full, providerGun, stick, capCatalog).inserted());
    }

    @Test void ejectionReturnsPhysicalAndDeferredRoundsAndClearsWithoutResettingIndex() {
        var stick = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("stick")));
        var spent = stick.copy(); spent.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var state = new RevolverState(2, List.of(new RevolverState.Chamber(true, Optional.empty()),
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")), new RevolverState.Chamber(false, Optional.of(spent)), RevolverState.Chamber.empty()));
        var result = RevolverProviderKernel.ejectAll(state, dummyGun());
        assertEquals(2, result.state().index()); assertEquals(4, result.state().chambers().size());
        assertEquals(3, result.ejected().size()); assertEquals(id("cartridge_magnum"), result.ejected().get(0).deferredPrototype().orElseThrow());
        assertFalse(result.ejected().get(0).spent()); assertEquals(id("cartridge_magnum"), result.ejected().get(1).deferredPrototype().orElseThrow());
        assertTrue(result.ejected().get(1).spent()); assertTrue(result.ejected().get(2).spent());
        assertEquals(0, RevolverProviderKernel.occupiedCount(result.state())); assertEquals(0, RevolverProviderKernel.unspentCount(result.state()));
        assertEquals(3, RevolverProviderKernel.occupiedCount(state)); assertEquals(1, RevolverProviderKernel.unspentCount(state));
    }

    private static GunProfile gunWithTags(Set<ResourceLocation> tags) {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(6, id("cartridge_magnum"), tags, Set.of())));
    }
}
