package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Typed owner for source random plant mutation lists. */
public final class ModPlantMutations {
    public static final PrototypeType<PlantMutationTable> TYPE = new PrototypeType<>(id("plant_mutation_table"),
            "moonstation14/plant_mutation_table", PlantMutationTable.CODEC);
    private ModPlantMutations() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static ResourceLocation tableId(String path) { return id(path); }
    public static PrototypeCatalog<PlantMutationTable> serverCatalog() { return PrototypeRuntime.serverPlantMutationTables(); }
    public static PrototypeCatalog<PlantMutationTable> clientCatalog() { return PrototypeRuntime.clientPlantMutationTables(); }
}
