package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GunWorldUsePolicyTest {
    private static net.minecraft.resources.ResourceLocation id(String path) { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static GunProfile profile(int capacity) {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(capacity, id("cartridge_magnum"), Set.of(), Set.of())));
    }

    @Test void insertStagesDetachedRoundAndPreservesIndexAndGunComponents() {
        var cartridgeId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick");
        var tag = id("cap");
        var gunProfile = new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(2, id("cartridge_magnum"), Set.of(tag), Set.of())));
        var gun = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        gun.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("gun"));
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), new GunRuntimeState(8, 7, 1.5f, 12));
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), new RevolverState(1,
                List.of(RevolverState.Chamber.empty(), RevolverState.Chamber.spentDeferred(id("cartridge_magnum")))));
        var spent = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        spent.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("spent round"));
        spent.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var originalGun = gun.copy(); var originalRound = spent.copy();
        var catalog = Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(tag)));
        var plan = GunWorldUsePolicy.prepareInsert(gunProfile, gun, spent, catalog);
        assertTrue(plan.accepted());
        var state = plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        assertEquals(1, state.index()); // only chamber zero was truly empty; index does not move
        var inserted = state.chambers().get(0).physical().orElseThrow();
        assertTrue(Boolean.TRUE.equals(inserted.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertEquals(originalRound.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), inserted.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertEquals(originalGun.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()), plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()));
        assertEquals(originalGun.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.gun().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(originalGun, gun));
        assertTrue(net.minecraft.world.item.ItemStack.matches(originalRound, spent));
        plan.gun().setCount(9);
        assertEquals(1, plan.gun().getCount());
    }

    @Test void insertRejectsWrongTagAndFullChambersWithoutMutatingGun() {
        var cartridgeId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick");
        var profile = new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(1, id("cartridge_magnum"), Set.of(id("cap")), Set.of())));
        var gun = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var original = gun.copy(); var round = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var wrong = GunWorldUsePolicy.prepareInsert(profile, gun, round,
                Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(id("magnum")))));
        assertFalse(wrong.accepted()); assertEquals(RevolverProviderKernel.InsertFailure.WRONG_TAG, wrong.failure());
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),
                new RevolverState(0, List.of(new RevolverState.Chamber(true, Optional.empty()))));
        original = gun.copy();
        var full = GunWorldUsePolicy.prepareInsert(profile, gun, round,
                Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(id("cap")))));
        assertFalse(full.accepted()); assertEquals(RevolverProviderKernel.InsertFailure.FULL, full.failure());
        assertTrue(net.minecraft.world.item.ItemStack.matches(original, gun));
    }

    @Test void spinStagesIndexOnlyAndPreservesRichStackAndRounds() {
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("marked"));
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), new GunRuntimeState(8, 7, 1.5f, 12));
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.initialize(profile(6)));
        var old = source.copy();
        var staged = GunWorldUsePolicy.prepareSpin(profile(6), source, 4);
        assertFalse(staged.isEmpty());
        assertEquals(4, staged.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).index());
        assertEquals(old.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers(), staged.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers());
        assertEquals(old.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()), staged.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()));
        assertEquals(old.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), staged.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(old, source));
    }

    @Test void emptyStateDoesNotRefillButAbsentStateInitializesAndBadIndicesReject() {
        var profile = profile(6);
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverState.empty(6));
        assertEquals(RevolverState.empty(6).chambers(), GunWorldUsePolicy.prepareSpin(profile, source, 2).get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers());
        var absent = GunWorldUsePolicy.prepareSpin(profile, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), 2);
        assertEquals(6, absent.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers().size());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile, source, 6).isEmpty());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile, source, -1).isEmpty());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile(5), source, 1).isEmpty());
    }

    @Test void emptyPlanPreservesIndexMaterializesLiveAndSpentAndRetainsPhysicalRounds() {
        var profile = profile(4);
        var rich = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        rich.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("round"));
        var state = new RevolverState(2, List.of(new RevolverState.Chamber(true, Optional.empty()),
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")),
                new RevolverState.Chamber(false, Optional.of(rich)), RevolverState.Chamber.empty()));
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), state);
        source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("gun"));
        var original = source.copy();
        var plan = GunWorldUsePolicy.prepareEmpty(profile, source);
        assertNotNull(plan);
        var emptied = plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        assertEquals(2, emptied.index());
        assertTrue(emptied.chambers().stream().allMatch(c -> !c.virtualLive() && c.physical().isEmpty() && c.spentCasing().isEmpty()));
        assertEquals(3, plan.outputs().size());
        assertFalse(Boolean.TRUE.equals(plan.outputs().get(0).get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertTrue(Boolean.TRUE.equals(plan.outputs().get(1).get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertEquals(rich.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.outputs().get(2).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(original, source));
        assertEquals(original.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.gun().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));

        var absent = GunWorldUsePolicy.prepareEmpty(profile(6), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        assertNotNull(absent);
        assertEquals(6, absent.outputs().size());
        var explicitEmpty = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        explicitEmpty.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverState.empty(6));
        assertTrue(GunWorldUsePolicy.prepareEmpty(profile(6), explicitEmpty).outputs().isEmpty());
    }
}
