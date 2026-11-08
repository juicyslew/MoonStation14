package com.juicyslew.moonstation14.ms14.construction.tool;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Prototype IDs and public capability queries for supported construction tools. */
public final class ModTools {
    public static final ResourceLocation BASE = id("base_tool");
    public static final ResourceLocation WRENCH = id("wrench");
    public static final ResourceLocation SCREWDRIVER = id("screwdriver");
    public static final ResourceLocation WELDER = id("welder");
    public static final ResourceLocation CROWBAR = id("crowbar");
    public static final PrototypeType<ToolPrototypeData> TOOL_TYPE = new PrototypeType<>(
            id("construction_tool"), "moonstation14/construction_tool", ToolPrototypeData.CODEC,
            ModTools::validateInheritance);

    private ModTools() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }

    private static void validateInheritance(PrototypeCatalog<JsonObject> raw, PrototypeCatalog<JsonObject> resolved) {
        JsonObject base = raw.get(BASE);
        if (base == null || !base.has("abstract") || !base.get("abstract").getAsBoolean()
                || base.has("parent") || !base.has("qualities") || !base.has("speedModifier"))
            throw new IllegalArgumentException("invalid abstract BaseItem tool parent");
        if (resolved.get(BASE) != null) throw new IllegalArgumentException("abstract tool parent was published");
        for (ResourceLocation childId : new ResourceLocation[]{WRENCH, SCREWDRIVER, WELDER, CROWBAR}) {
            JsonObject child = raw.get(childId);
            if (child == null || !child.has("parent") || !BASE.getPath().equals(child.get("parent").getAsString()))
                throw new IllegalArgumentException("tool must inherit base_tool: " + childId);
        }
        JsonObject welder = raw.get(WELDER);
        if (welder == null || !welder.has("welder") || !welder.has("qualities")
                || !welder.getAsJsonArray("qualities").toString().contains("Welding"))
            throw new IllegalArgumentException("welder must declare fueled Welding capability");
    }

    public static boolean hasQuality(PrototypeCatalog<ToolPrototypeData> catalog, ResourceLocation prototype,
                                    ToolPrototypeData.Quality quality) {
        ToolPrototypeData tool = catalog.get(prototype);
        return quality != ToolPrototypeData.Quality.Welding && tool != null && tool.qualities().contains(quality);
    }

    public static boolean hasQuality(PrototypeCatalog<ToolPrototypeData> catalog, ResourceLocation prototype,
                                     ToolPrototypeData.Quality quality, WelderFuelData fuel) {
        ToolPrototypeData tool = catalog.get(prototype);
        if (tool == null || !tool.qualities().contains(quality)) return false;
        return quality != ToolPrototypeData.Quality.Welding || fuel != null && fuel.lit() && fuel.fuel() > 0;
    }

    public static Optional<Double> speedModifier(PrototypeCatalog<ToolPrototypeData> catalog,
                                                  ResourceLocation prototype,
                                                  ToolPrototypeData.Quality requiredQuality) {
        ToolPrototypeData tool = catalog.get(prototype);
        return tool != null && tool.qualities().contains(requiredQuality)
                ? Optional.of(tool.speedModifier()) : Optional.empty();
    }

    /** Construction-step capability lookup; caller supplies the resolved item tool prototype. */
    public static boolean canPryConstructionStep(PrototypeCatalog<ToolPrototypeData> catalog,
                                                 ResourceLocation toolPrototype) {
        return hasQuality(catalog, toolPrototype, ToolPrototypeData.Quality.Prying);
    }

    /** Resolve a held stack through its registered item id to the local tool prototype, then query its capability. */
    public static boolean canPryConstructionStep(PrototypeCatalog<ToolPrototypeData> catalog, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return itemPrototype(BuiltInRegistries.ITEM.getResourceKey(stack.getItem()).map(key -> key.location()))
                .map(toolPrototype -> canPryConstructionStep(catalog, toolPrototype)).orElse(false);
    }

    static Optional<ResourceLocation> itemPrototype(Optional<ResourceLocation> itemId) {
        if (itemId == null || itemId.isEmpty()) return Optional.empty();
        ResourceLocation registeredId = itemId.get();
        if (!MoonStation14.MOD_ID.equals(registeredId.getNamespace())) return Optional.empty();
        return Optional.of(id(registeredId.getPath()));
    }
}

