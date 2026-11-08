package com.juicyslew.moonstation14.ms14.power.device;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModPowerAppearances {
    public static final ResourceLocation SOURCE = id("hv_source");
    public static final ResourceLocation SUBSTATION = id("hv_mv_substation");
    public static final ResourceLocation APC = id("apc");
    public static final PrototypeType<PowerAppearanceData> TYPE = new PrototypeType<>(id("power_appearance"),
            "moonstation14/power_appearance", PowerAppearanceData.CODEC, catalog -> {
                for (PowerAppearanceData d : catalog.asMap().values()) d.validate();
                require(catalog, SOURCE, "hv_source"); require(catalog, SUBSTATION, "hv_mv_substation"); require(catalog, APC, "apc");
            });
    private ModPowerAppearances() { }
    public static PrototypeCatalog<PowerAppearanceData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientPowerAppearances() : PrototypeRuntime.serverPowerAppearances();
    }
    private static void require(PrototypeCatalog<PowerAppearanceData> c, ResourceLocation id, String kind) {
        PowerAppearanceData d=c.get(id); if(d==null || !d.kind().equals(kind)) throw new IllegalArgumentException("missing power appearance " + id);
    }
    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,s); }
}

