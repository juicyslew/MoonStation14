package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModRadiationCollectors {
    public static final ResourceLocation EMPTY_ID = id("radiation_collector");
    public static final ResourceLocation NO_TANK_ID = id("radiation_collector_no_tank");
    public static final ResourceLocation FULL_TANK_ID = id("radiation_collector_full_tank");
    public static final PrototypeType<RadiationCollectorProfile> TYPE = RadiationCollectorProfile.TYPE;
    private ModRadiationCollectors() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    public static PrototypeCatalog<RadiationCollectorProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
}

