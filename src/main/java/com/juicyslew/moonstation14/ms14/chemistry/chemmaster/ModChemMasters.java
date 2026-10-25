package com.juicyslew.moonstation14.ms14.chemistry.chemmaster;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModChemMasters {
    public static final ResourceLocation BASE_ID = id("base");
    public static final ResourceLocation CHEM_MASTER_ID = id("chemmaster");
    public static final PrototypeType<ChemMasterData> TYPE = new PrototypeType<>(id("chemmaster"), "moonstation14/chemmaster",
            ChemMasterData.CODEC, new ChemMasterMergeStrategy());
    private ModChemMasters() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<ChemMasterData> catalog(Level level) { return level.isClientSide() ? PrototypeRuntime.clientChemMasters() : PrototypeRuntime.serverChemMasters(); }
    public static ChemMasterData resolved(Level level, ResourceLocation id) { return level == null ? null : catalog(level).get(id); }
}

