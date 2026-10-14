package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ModChemContainers {
    public static final PrototypeType<ChemContainerData> TYPE = new PrototypeType<>(id("chem_container"),
            "moonstation14/chem_container", ChemContainerData.CODEC, new ChemContainerMergeStrategy());
    private ModChemContainers() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<ChemContainerData> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
    public static ChemContainerData resolve(Level level, ResourceLocation id) { return level == null ? null : catalog(level).get(id); }
    public static ChemContainerData resolveServer(ResourceLocation id) { return PrototypeRuntime.serverManager().snapshot(TYPE).get(id); }
    public static ResourceLocation prototype(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(com.juicyslew.moonstation14.item.ModItems.BOTTLE)) return id("bottle");
        if (stack.is(com.juicyslew.moonstation14.item.ModItems.PILL)) return id("pill");
        if (stack.is(com.juicyslew.moonstation14.item.ModItems.PILL_CANISTER)) return id("pill_canister");
        return null;
    }
    public static ChemContainerData resolve(Level level, ItemStack stack) { return resolve(level, prototype(stack)); }
}
