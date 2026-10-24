package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModMeleeArcProfiles {
    public static final PrototypeType<MeleeArcProfile> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "melee_arc_profile"), "moonstation14/melee_arc_profile", MeleeArcProfile.CODEC);
    private ModMeleeArcProfiles() { }
    public static PrototypeCatalog<MeleeArcProfile> serverCatalog() { return PrototypeRuntime.serverMeleeArcProfiles(); }
    public static PrototypeCatalog<MeleeArcProfile> clientCatalog() { return PrototypeRuntime.clientMeleeArcProfiles(); }
}
