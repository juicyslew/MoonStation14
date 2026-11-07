package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Copy-only ammo prediction; its staged result is presentation-only and is never published. */
public final class ClientGunShotPrediction {
    private ClientGunShotPrediction() { }

    public static GunAmmoProviderKernel.TakeResult takeOne(ItemStack staged, GunProfile profile, GunRuntimeState runtime,
            Map<ResourceLocation, CartridgeProfile> cartridges, Map<ResourceLocation, ProjectileProfile> projectiles,
            Map<ResourceLocation, BatteryProfile> batteries) {
        if (profile.batteryAmmoProvider().isPresent())
            return BatteryAmmoProviderKernel.take(staged.copy(), profile, batteries, projectiles,
                    GunModePolicy.selectedBatteryMode(runtime, profile));
        return GunAmmoProviderKernel.takeOne(staged.copy(), profile, cartridges, projectiles);
    }

    public static boolean effectivePacifismAllowed(GunRuntimeState runtime, GunProfile profile) {
        return GunModePolicy.effectivePacifismAllowed(runtime, profile);
    }

    /** Shared cue selection for the live client and deterministic feedback policy tests. */
    public static java.util.Optional<PositionalSoundCue> feedbackCue(GunProfile gun, GunAmmoProviderKernel.Status status) {
        if (gun == null || status == null || status == GunAmmoProviderKernel.Status.INVALID) return java.util.Optional.empty();
        return status == GunAmmoProviderKernel.Status.EMPTY || status == GunAmmoProviderKernel.Status.DRY_FIRE
                ? gun.gun().emptySound() : gun.gun().gunshotSound();
    }
}
