package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagazineChamberProviderKernelTest {
    @Test void emptyStateCodecRoundTrips() {
        var state=MagazineChamberState.empty(false);
        var encoded=MagazineChamberState.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow();
        assertEquals(state,MagazineChamberState.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow());
    }

    @Test void removingMagazineRetainsIndependentChamber() {
        var chamber=new ItemStack(net.minecraft.world.item.Items.STICK);
        var state=new MagazineChamberState(new ItemStack(net.minecraft.world.item.Items.PAPER),chamber,true);
        var removed=MagazineChamberProviderKernel.ejectMagazine(state);
        assertTrue(ItemStack.isSameItemSameComponents(chamber, removed.chamber()));
        assertEquals(chamber.getCount(), removed.chamber().getCount());
        assertNotSame(chamber, removed.chamber());
        assertTrue(removed.magazine().isEmpty());
    }

    @Test void rackOnClosedBoltEjectsExistingRoundAndDoesNotLoseIt() {
        var gun=gun(false);
        var oldRound=new ItemStack(net.minecraft.world.item.Items.STICK);
        var result=MagazineChamberProviderKernel.rack(new MagazineChamberState(ItemStack.EMPTY,oldRound,true),gun);
        var ejected = result.ejected().orElseThrow();
        assertTrue(ItemStack.isSameItemSameComponents(oldRound, ejected));
        assertEquals(oldRound.getCount(), ejected.getCount());
        assertNotSame(oldRound, ejected);
        assertTrue(result.state().chamber().isEmpty());
        assertTrue(result.state().boltClosed());
    }

    @Test void manualRackClosesEmptyBoltAndDoesNotInventAChamberRound() {
        var result=MagazineChamberProviderKernel.rack(new MagazineChamberState(ItemStack.EMPTY,ItemStack.EMPTY,false),gun(false));
        assertTrue(result.state().boltClosed());
        assertTrue(result.state().chamber().isEmpty());
        assertTrue(result.ejected().isEmpty());
    }

    @Test void autoCycleOpensBoltWithEmptyMagazineItemButNotWhenMagazineAbsent() {
        var profile=gun(true);
        var mag=new ItemStack(net.minecraft.world.item.Items.PAPER);
        var round=new ItemStack(net.minecraft.world.item.Items.STICK);
        mag.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(java.util.List.of()));
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
        var cartridges=java.util.Map.of(id,new CartridgeProfile(ResourceLocation.withDefaultNamespace("arrow"),false,java.util.Set.of(ResourceLocation.fromNamespaceAndPath("test","rounds"))));
        var projectiles=java.util.Map.of(ResourceLocation.withDefaultNamespace("arrow"),new ProjectileProfile(java.util.Map.of(),false,true,10));
        var withMag=MagazineChamberProviderKernel.take(new ItemStack(net.minecraft.world.item.Items.BOW),new MagazineChamberState(mag,round,true),profile,cartridges,projectiles);
        assertEquals(GunAmmoProviderKernel.Status.READY,withMag.status());
        var magState=withMag.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
        assertFalse(magState.magazine().isEmpty());
        assertTrue(magState.chamber().isEmpty());
        assertFalse(magState.boltClosed());
        assertTrue(withMag.ejectedRound().isPresent());
        assertTrue(withMag.ejectedRound().orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        var absent=MagazineChamberProviderKernel.take(new ItemStack(net.minecraft.world.item.Items.BOW),new MagazineChamberState(ItemStack.EMPTY,round,true),profile,cartridges,projectiles);
        assertEquals(GunAmmoProviderKernel.Status.READY,absent.status());
        var absentState=absent.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
        assertTrue(absentState.magazine().isEmpty());
        assertTrue(absentState.chamber().isEmpty());
        assertTrue(absentState.boltClosed());
    }

    @Test void feedPreservesOpenBoltChamberWithoutMagazineAndManualRackEjectsThenFeeds() {
        var round=new ItemStack(net.minecraft.world.item.Items.STICK);
        var open=MagazineChamberProviderKernel.rack(new MagazineChamberState(ItemStack.EMPTY,round,false),gun(false));
        assertTrue(open.state().boltClosed());
        assertTrue(ItemStack.isSameItemSameComponents(round,open.state().chamber()));
        var magazine=new ItemStack(net.minecraft.world.item.Items.PAPER);
        magazine.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(java.util.List.of(round)));
        var closed=MagazineChamberProviderKernel.rack(new MagazineChamberState(magazine,round,true),gun(false));
        assertTrue(closed.ejected().isPresent());
        assertTrue(ItemStack.isSameItemSameComponents(round,closed.state().chamber()));
    }

    @Test void liveNonAutoCycleRoundBecomesSpentAndCannotFireTwice() {
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
        var round=new ItemStack(net.minecraft.world.item.Items.STICK);
        var cartridges=java.util.Map.of(id,new CartridgeProfile(ResourceLocation.withDefaultNamespace("arrow"),false,java.util.Set.of(ResourceLocation.fromNamespaceAndPath("test","rounds"))));
        var projectiles=java.util.Map.of(ResourceLocation.withDefaultNamespace("arrow"),new ProjectileProfile(java.util.Map.of(),false,true,10));
        var state=new MagazineChamberState(ItemStack.EMPTY,round,true);
        var fired=MagazineChamberProviderKernel.take(new ItemStack(net.minecraft.world.item.Items.BOW),state,gun(false),cartridges,projectiles);
        assertEquals(GunAmmoProviderKernel.Status.READY,fired.status());
        assertTrue(fired.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).chamber().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        var next=MagazineChamberProviderKernel.take(fired.gun(),fired.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()),gun(false),cartridges,projectiles);
        assertEquals(GunAmmoProviderKernel.Status.DRY_FIRE,next.status());
        assertTrue(next.projectile()==null);
        assertTrue(next.ejectedRound().isEmpty());
    }

    @Test void autoCycleEjectsExactlyOneSpentCase() {
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
        var round=new ItemStack(net.minecraft.world.item.Items.STICK);
        var cartridges=java.util.Map.of(id,new CartridgeProfile(ResourceLocation.withDefaultNamespace("arrow"),false,java.util.Set.of(ResourceLocation.fromNamespaceAndPath("test","rounds"))));
        var projectiles=java.util.Map.of(ResourceLocation.withDefaultNamespace("arrow"),new ProjectileProfile(java.util.Map.of(),false,true,10));
        var result=MagazineChamberProviderKernel.take(new ItemStack(net.minecraft.world.item.Items.BOW),new MagazineChamberState(ItemStack.EMPTY,round,true),gun(true),cartridges,projectiles);
        assertEquals(GunAmmoProviderKernel.Status.READY,result.status());
        assertTrue(result.ejectedRound().isPresent());
        assertTrue(result.ejectedRound().orElseThrow().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        assertTrue(result.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).chamber().isEmpty());
    }

    private static GunProfile gun(boolean auto) {
        var provider=new GunProfile.MagazineChamberProviderData("mag","chamber",java.util.Set.of(ResourceLocation.fromNamespaceAndPath("test","mags")),java.util.Set.of(ResourceLocation.fromNamespaceAndPath("test","rounds")),java.util.Optional.empty(),java.util.Optional.empty(),auto,true,false);
        return new GunProfile(new GunProfile.GunData(1,1,true,"SEMI_AUTO",java.util.Optional.empty(),java.util.Optional.empty()),java.util.Optional.empty(),false,false,java.util.Optional.of(provider),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),java.util.Optional.empty(),false,1f,java.util.Optional.empty(),java.util.Optional.empty());
    }
}
