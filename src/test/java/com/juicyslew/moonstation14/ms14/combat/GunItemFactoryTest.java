package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GunItemFactoryTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static GunProfile basic() { return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()), Optional.empty()); }
    private static GunProfile ballistic(int capacity) {
        return new GunProfile(basic().gun(), Optional.empty(), false, false, Optional.empty(),
                Optional.of(new GunProfile.BallisticProviderData(capacity, id("ammo"), id("round"), true)), Optional.empty(),
                Optional.empty(), Optional.empty(), false, 1, Optional.empty(), Optional.empty());
    }
    private static GunItemFactory factory(Map<ResourceLocation,GunProfile> guns, Map<ResourceLocation,AmmoContainerProfile> ammo,
                                          Map<ResourceLocation,BatteryProfile> batteries) {
        return new GunItemFactory(new PrototypeCatalog<>(guns), new PrototypeCatalog<>(ammo), new PrototypeCatalog<>(batteries));
    }

    @Test void ballisticInitIsIdempotentAndDoesNotRefillPresentEmptyStateOrMutateSource() {
        ResourceLocation gunId = BuiltInRegistries.ITEM.getKey(Items.STICK);
        var gun = new ItemStack(Items.STICK);
        var factory = factory(Map.of(gunId, ballistic(5)), Map.of(), Map.of());
        var initialized = factory.initializeOwnedCopy(gun);
        assertEquals(5, initialized.get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get()).count());
        assertNull(gun.get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get()));
        initialized.set(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get(), new BallisticState(java.util.List.of(), 0, id("round")));
        assertEquals(0, factory.initializeOwnedCopy(initialized).get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get()).count());
    }

    @Test void explicitZeroBatteryChargeStaysDepleted() {
        ResourceLocation cellId = BuiltInRegistries.ITEM.getKey(Items.BOWL);
        var cell = new ItemStack(Items.BOWL);
        var factory = factory(Map.of(), Map.of(), Map.of(cellId, new BatteryProfile(360, 120, Set.of())));
        var charged = factory.initializeOwnedCopy(cell);
        assertEquals(120, charged.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        charged.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(), new BatteryState(0));
        assertEquals(0, factory.initializeOwnedCopy(charged).get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
    }

    @Test void recursiveStartingCompositionRejectsCycles() {
        var firstId = BuiltInRegistries.ITEM.getKey(Items.STICK);
        var secondId = BuiltInRegistries.ITEM.getKey(Items.BOWL);
        var provider = new GunProfile.MagazineChamberProviderData("mag", "chamber", Set.of(id("mag")), Set.of(id("ammo")), Optional.of(secondId), Optional.empty(), true, true, false);
        var profile = new GunProfile(basic().gun(), Optional.empty(), false, false, Optional.of(provider), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, 1, Optional.empty(), Optional.empty());
        var childProvider = new GunProfile.MagazineChamberProviderData("mag", "chamber", Set.of(id("mag")), Set.of(id("ammo")), Optional.of(firstId), Optional.empty(), true, true, false);
        var child = new GunProfile(basic().gun(), Optional.empty(), false, false, Optional.of(childProvider), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, 1, Optional.empty(), Optional.empty());
        var factory = factory(Map.of(firstId, profile, secondId, child), Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> factory.initializeOwnedCopy(new ItemStack(Items.STICK)));
    }
}
