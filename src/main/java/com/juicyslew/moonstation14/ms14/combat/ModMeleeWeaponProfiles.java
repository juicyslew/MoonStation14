package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Catalog access for resolved melee weapon profiles. */
public final class ModMeleeWeaponProfiles {
    public static final PrototypeType<MeleeWeaponProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "melee_weapon_profile"),
            "moonstation14/melee_weapon_profile", MeleeWeaponProfile.CODEC, new MeleeWeaponProfileMergeStrategy());
    private ModMeleeWeaponProfiles() { }
    public static PrototypeCatalog<MeleeWeaponProfile> serverCatalog() { return PrototypeRuntime.serverMeleeWeaponProfiles(); }
    public static PrototypeCatalog<MeleeWeaponProfile> clientCatalog() { return PrototypeRuntime.clientMeleeWeaponProfiles(); }
}
