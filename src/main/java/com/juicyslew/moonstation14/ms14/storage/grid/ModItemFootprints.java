package com.juicyslew.moonstation14.ms14.storage.grid;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public final class ModItemFootprints {
    public static final PrototypeType<ItemFootprintProfile> TYPE = new PrototypeType<>(id("item_footprint"), "moonstation14/item_footprint", ItemFootprintProfile.CODEC);
    private ModItemFootprints() { }
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, s); }
    public static PrototypeCatalog<ItemFootprintProfile> serverCatalog() { return PrototypeRuntime.serverItemFootprints(); }
    public static PrototypeCatalog<ItemFootprintProfile> clientCatalog() { return PrototypeRuntime.clientItemFootprints(); }
    public static ItemFootprintProfile get(PrototypeCatalog<ItemFootprintProfile> c, ResourceLocation id) {
        Objects.requireNonNull(c); ItemFootprintProfile p = id == null ? null : c.get(id);
        return p == null ? null : p;
    }
}
