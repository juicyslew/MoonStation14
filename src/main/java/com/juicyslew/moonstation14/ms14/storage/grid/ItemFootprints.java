package com.juicyslew.moonstation14.ms14.storage.grid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;

import java.util.List;

/** Bounded initial footprint policy. Unknown item identities deliberately remain one cell. */
public final class ItemFootprints {
    private static final GridGeometry.Footprint ONE = GridGeometry.Footprint.of(List.of(new GridGeometry.Cell(0, 0)));

    private ItemFootprints() { }

    public static GridGeometry.Footprint forStack(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return serverForId(id);
    }

    /** Pure legacy/test helper; production callers must select an explicit side. */
    public static GridGeometry.Footprint forId(ResourceLocation id) {
        return serverForId(id);
    }
    public static GridGeometry.Footprint serverForId(ResourceLocation id) { return from(ModItemFootprints.serverCatalog(), com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(false), id); }
    public static GridGeometry.Footprint clientForId(ResourceLocation id) { return from(ModItemFootprints.clientCatalog(), com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(true), id); }
    public static boolean clientCatalogIsLoaded() { return ModItemFootprints.clientCatalog().size() > 0; }
    public static boolean clientHasProfile(ResourceLocation id) {
        return ModItemFootprints.get(ModItemFootprints.clientCatalog(), id) != null
                || id != null && com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalogForSide(true).get(id) != null;
    }
    private static GridGeometry.Footprint from(PrototypeCatalog<ItemFootprintProfile> catalog, PrototypeCatalog<com.juicyslew.moonstation14.ms14.medical.MedicalItemData> medical, ResourceLocation id) {
        ItemFootprintProfile profile = ModItemFootprints.get(catalog, id);
        if (profile == null && id != null && "moonstation14".equals(id.getNamespace())) {
            var resolved = medical.get(id);
            if (resolved != null) {
                boolean small = resolved.item().size().equals("Small");
                return small ? GridGeometry.Footprint.of(List.of(new GridGeometry.Cell(0,0), new GridGeometry.Cell(0,1))) : ONE;
            }
        }
        return profile == null ? ONE : profile.footprint();
    }
}
