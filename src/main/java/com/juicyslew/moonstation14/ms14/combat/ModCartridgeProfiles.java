package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
public final class ModCartridgeProfiles {
    public static final PrototypeType<CartridgeProfile> TYPE = new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"cartridge_profile"), "moonstation14/cartridge_profile", CartridgeProfile.CODEC, new CartridgeProfileMergeStrategy());
    private ModCartridgeProfiles() {}
    public static PrototypeCatalog<CartridgeProfile> serverCatalog() { return PrototypeRuntime.serverCartridgeProfiles(); }
    public static PrototypeCatalog<CartridgeProfile> clientCatalog() { return PrototypeRuntime.clientCartridgeProfiles(); }
}
