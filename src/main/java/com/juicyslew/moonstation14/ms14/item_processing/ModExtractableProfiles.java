package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModExtractableProfiles {
    public static final PrototypeType<ExtractableProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "extractable_profile"),
            "moonstation14/extractable_profile", ExtractableProfile.CODEC);
    private ModExtractableProfiles() { }
    public static PrototypeCatalog<ExtractableProfile> serverCatalog() { return PrototypeRuntime.serverExtractableProfiles(); }
    public static PrototypeCatalog<ExtractableProfile> clientCatalog() { return PrototypeRuntime.clientExtractableProfiles(); }
}
