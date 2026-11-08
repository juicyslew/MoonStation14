package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;

/** Resolved local charger profile family. */
public final class ModChargerProfiles {
    private ModChargerProfiles() { }
    public static final PrototypeType<ChargerProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "charger_profile"),
            "moonstation14/charger_profile", ChargerProfile.CODEC, new ChargerProfileMergeStrategy());
    public static PrototypeCatalog<ChargerProfile> serverCatalog() { return PrototypeRuntime.serverChargerProfiles(); }
}
