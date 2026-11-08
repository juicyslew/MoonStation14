package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModGasMiners {
    public static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, name); }
    public static final PrototypeType<GasMinerProfile> TYPE = new PrototypeType<>(
            id("gas_miner"),
            "moonstation14/gas_miner", GasMinerProfile.CODEC, catalog -> { });
    private ModGasMiners() { }
    public static PrototypeCatalog<GasMinerProfile> catalog(Level level) {
        return (level.isClientSide() ? com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager()
                : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}
