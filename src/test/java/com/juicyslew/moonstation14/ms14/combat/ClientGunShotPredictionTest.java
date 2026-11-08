package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.ClientGunShotPrediction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientGunShotPredictionTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static GunProfile gun(List<GunProfile.BatteryFireModeData> modes) {
        var data = new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty());
        return new GunProfile(data,Optional.empty(),false,false,Optional.empty(),Optional.empty(),
                Optional.of(new GunProfile.BatteryAmmoProviderData(id("default_shot"),10)),Optional.empty(),Optional.empty(),false,1,
                Optional.empty(),Optional.of(modes));
    }
    private static GunRuntimeState mode(int index) { return new GunRuntimeState(0,0,0,0,"",index); }
    private static ProjectileProfile projectile() { return new ProjectileProfile(Map.of(),false,true,10); }

    @Test void selectedNonlethalModeChoosesItsShotAndDoesNotMutateInput() {
        var gun = gun(List.of(new GunProfile.BatteryFireModeData(id("lethal"),10,false),
                new GunProfile.BatteryFireModeData(id("stun"),25,true)));
        var input = new ItemStack(Items.STICK);
        input.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(100));
        var result = ClientGunShotPrediction.takeOne(input,gun,mode(1),Map.of(),
                Map.of(id("lethal"),projectile(),id("stun"),projectile()),
                 Map.of(ResourceLocation.withDefaultNamespace("stick"),new BatteryProfile(100,100,Set.of())));
        assertEquals(GunAmmoProviderKernel.Status.READY,result.status());
        assertEquals(id("stun"),result.projectile());
        assertEquals(100,input.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(75,result.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertTrue(ClientGunShotPrediction.effectivePacifismAllowed(mode(1),gun));
        assertFalse(ClientGunShotPrediction.effectivePacifismAllowed(mode(0),gun));
    }

    @Test void emptyBatteryIsNotRefilledByPredictionAndUnknownProjectileIsInvalid() {
        var gun = gun(List.of(new GunProfile.BatteryFireModeData(id("stun"),10,true)));
        var input = new ItemStack(Items.STICK);
        input.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(0));
        var batteries = Map.of(ResourceLocation.withDefaultNamespace("stick"),new BatteryProfile(100,100,Set.of()));
        var empty = ClientGunShotPrediction.takeOne(input,gun,mode(0),Map.of(),Map.of(id("stun"),projectile()),batteries);
        assertEquals(GunAmmoProviderKernel.Status.EMPTY,empty.status());
        assertEquals(0,input.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(GunAmmoProviderKernel.Status.INVALID,
                ClientGunShotPrediction.takeOne(input,gun,mode(0),Map.of(),Map.of(),batteries).status());
    }
}
