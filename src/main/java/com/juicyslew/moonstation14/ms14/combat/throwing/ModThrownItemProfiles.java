package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;

/** Catalog access for resolved thrown-item capabilities. */
public final class ModThrownItemProfiles {
    public static final PrototypeType<ThrownItemProfile> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"thrown_item_profile"), "moonstation14/thrown_item_profile", ThrownItemProfile.CODEC, new ThrownItemProfileMergeStrategy());
    private ModThrownItemProfiles() { }
    public static PrototypeCatalog<ThrownItemProfile> serverCatalog() { return PrototypeRuntime.serverThrownItemProfiles(); }
    public static PrototypeCatalog<ThrownItemProfile> clientCatalog() { return PrototypeRuntime.clientThrownItemProfiles(); }
}
