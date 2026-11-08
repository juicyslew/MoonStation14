package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatteryProviderKernelTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    @Test void profileAndChargeRejectNonFiniteAndOutOfCapacityValuesAndCodecDefaults() {
        var profile = BatteryProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"capacity\":1000,\"tags\":[\"moonstation14:power_cell\"]}")).getOrThrow();
        assertEquals(0, profile.startingCharge()); assertTrue(profile.tags().contains(id("power_cell")));
        assertThrows(IllegalArgumentException.class, () -> new BatteryProfile(Float.NaN, 0, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new BatteryProfile(10, 11, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new BatteryState(Float.POSITIVE_INFINITY));
        assertTrue(new GunPowerCellSlotState(Optional.of(ItemStack.EMPTY)).cell().isEmpty());
    }

    @Test void integralBatteryDebitsExactlyOnceAndNeverRefillsMissingState() {
        var gunId = ResourceLocation.withDefaultNamespace("stick");
        var gun = new ItemStack(Items.STICK);
        var profile = profile(1000, 1000, Set.of());
        var gp = batteryGun(id("laser"), 62.5f);
        var batteries = Map.of(gunId, profile);
        var projectiles = Map.of(id("laser"), new ProjectileProfile(Map.of(), false, true, 10),
                id("cellgun"), new ProjectileProfile(Map.of(), false, true, 10));
        for (int n=0;n<16;n++) {
            var result = BatteryAmmoProviderKernel.take(gun,gp,batteries,projectiles,0);
            assertEquals(GunAmmoProviderKernel.Status.READY,result.status()); gun=result.gun();
        }
        assertEquals(0f,gun.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(GunAmmoProviderKernel.Status.EMPTY,BatteryAmmoProviderKernel.take(gun,gp,batteries,projectiles,0).status());
    }

    @Test void selectedModeCostInvalidInputsAndOwnedCellRoundTrip() {
        var gunId=ResourceLocation.withDefaultNamespace("stick"); var gun=new ItemStack(Items.STICK);
        var modeGun=batteryGun(id("shotgun"),80f);
        var modes=List.of(new GunProfile.BatteryFireModeData(id("laser"),80,false),new GunProfile.BatteryFireModeData(id("disabler"),48,true));
        modeGun=new GunProfile(modeGun.gun(),modeGun.revolverAmmoProvider(),false,false,Optional.empty(),Optional.empty(),modeGun.batteryAmmoProvider(),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.of(modes));
        var batteries=Map.of(gunId,profile(480,480,Set.of()));
        var projectiles=Map.of(id("laser"),new ProjectileProfile(Map.of(),false,true,10),id("disabler"),new ProjectileProfile(Map.of(),false,true,10),id("cellgun"),new ProjectileProfile(Map.of(),false,true,10));
        assertEquals(432f,BatteryAmmoProviderKernel.take(gun,modeGun,batteries,projectiles,1).gun().get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(GunAmmoProviderKernel.Status.INVALID,BatteryAmmoProviderKernel.take(gun,modeGun,batteries,projectiles,2).status());
        assertEquals(GunAmmoProviderKernel.Status.INVALID,BatteryAmmoProviderKernel.take(gun,modeGun,batteries,Map.of(),0).status());

        var cell=new ItemStack(Items.STICK); cell.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("marked"));
        cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(70));
        var accepted=id("power_cell"); var slotGun=withSlot(batteryGun(id("cellgun"),10),accepted);
        var cells=Map.of(gunId,profile(100,100,Set.of(accepted)));
        var inserted=BatteryAmmoProviderKernel.insert(gun,slotGun,cell,cells); assertTrue(inserted.accepted());
        var fired=BatteryAmmoProviderKernel.take(inserted.gun(),slotGun,cells,projectiles,0); assertEquals(GunAmmoProviderKernel.Status.READY,fired.status());
        var ejected=BatteryAmmoProviderKernel.eject(fired.gun()); var returned=ejected.returnedCell().orElseThrow();
        assertEquals(60,returned.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(cell.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),returned.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(BatteryAmmoProviderKernel.insert(ejected.gun(),slotGun,returned,cells).accepted());
        assertFalse(BatteryAmmoProviderKernel.insert(gun,slotGun,cell,Map.of(gunId,profile(100,100,Set.of(id("wrong"))))).accepted());
        assertEquals(GunAmmoProviderKernel.Status.EMPTY,BatteryAmmoProviderKernel.take(gun,slotGun,cells,projectiles,0).status());
    }
    private static BatteryProfile profile(float cap,float start,Set<ResourceLocation> tags){return new BatteryProfile(cap,start,tags);}
    private static GunProfile batteryGun(ResourceLocation projectile,float cost) {
        return new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.empty(),Optional.empty(),Optional.of(new GunProfile.BatteryAmmoProviderData(projectile,cost)),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.empty());
    }
    private static GunProfile withSlot(GunProfile base,ResourceLocation tag) {
        return new GunProfile(base.gun(),base.revolverAmmoProvider(),false,false,Optional.empty(),Optional.empty(),base.batteryAmmoProvider(),Optional.empty(),Optional.empty(),false,1,Optional.of(new GunProfile.CellSlotData(id("power_cell"),Set.of(tag),Optional.empty())),Optional.empty());
    }
}
