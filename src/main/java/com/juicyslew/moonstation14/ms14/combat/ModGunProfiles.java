package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
public final class ModGunProfiles {
    public static final PrototypeType<GunProfile> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"gun_profile"), "moonstation14/gun_profile", GunProfile.CODEC, new GunProfileMergeStrategy());
    private ModGunProfiles() {}
    public static PrototypeCatalog<GunProfile> serverCatalog() { return PrototypeRuntime.serverGunProfiles(); }
    public static PrototypeCatalog<GunProfile> clientCatalog() { return PrototypeRuntime.clientGunProfiles(); }
}
