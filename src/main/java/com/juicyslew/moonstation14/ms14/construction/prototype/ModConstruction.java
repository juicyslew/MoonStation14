package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;

/** Prototype descriptors and validated read-only access to construction data. */
public final class ModConstruction {
    public static final PrototypeType<ConstructionGraphData> GRAPH_TYPE = new PrototypeType<>(id("construction_graph"), "moonstation14/construction_graph", ConstructionGraphData.CODEC);
    public static final PrototypeType<ConstructionRecipeData> RECIPE_TYPE = new PrototypeType<>(id("construction_recipe"), "moonstation14/construction_recipe", ConstructionRecipeData.CODEC);
    public static final PrototypeType<ConstructionEntityData> ENTITY_TYPE = new PrototypeType<>(id("construction_entity"), "moonstation14/construction_entity", ConstructionEntityData.CODEC);
    private ModConstruction() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,path); }
    /** Canonical local entity-prototype id (camelCase and hyphens map to snake_case). */
    public static ResourceLocation entityId(String name) {
        if(name==null||name.isBlank())throw new IllegalArgumentException("empty construction entity id");
        String normalized=name.replaceAll("([a-z0-9])([A-Z])","$1_$2").replace('-','_').toLowerCase(java.util.Locale.ROOT);
        return normalized.indexOf(':')<0?id(normalized):ResourceLocation.parse(normalized);
    }
    public static ConstructionCatalog catalog(PrototypeCatalog<ConstructionGraphData> graphs, PrototypeCatalog<ConstructionRecipeData> recipes) { return new ConstructionCatalog(graphs,recipes); }
}

