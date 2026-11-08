package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Registration metadata consumed by the platform prototype bootstrap. */
public final class ModTeslaProfiles {
    public static final ResourceLocation MAIN_ID = id("tesla_energy_ball");
    public static final ResourceLocation MINI_ID = id("tesla_mini_energy_ball");
    public static final ResourceLocation COIL_ID = id("tesla_coil");
    public static final ResourceLocation ROD_ID = id("tesla_grounding_rod");
    public static ResourceLocation profileForBlock(ResourceLocation block) {
        if (block == null || !"moonstation14".equals(block.getNamespace())) return null;
        return switch (block.getPath()) {
            case "tesla_coil" -> COIL_ID;
            case "tesla_grounding_rod" -> ROD_ID;
            case "emitter", "airlock", "chem_master", "apc", "hv_mv_substation" -> id(block.getPath());
            default -> null;
        };
    }
    public static final PrototypeType<TeslaBallProfile> BALL_TYPE = new PrototypeType<>(id("tesla_ball"), "moonstation14/tesla_ball", TeslaBallProfile.CODEC, c -> c.values().forEach(TeslaBallProfile::validate));
    public static final PrototypeType<TeslaMachineProfile> MACHINE_TYPE = new PrototypeType<>(id("tesla_machine"), "moonstation14/tesla_machine", TeslaMachineProfile.CODEC, c -> c.values().forEach(TeslaMachineProfile::validate));
    private ModTeslaProfiles() { }
    public static PrototypeCatalog<TeslaBallProfile> balls(Level level) { return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(BALL_TYPE); }
    public static PrototypeCatalog<TeslaMachineProfile> machines(Level level) { return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(MACHINE_TYPE); }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
