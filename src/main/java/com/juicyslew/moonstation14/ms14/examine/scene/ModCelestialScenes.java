package com.juicyslew.moonstation14.ms14.examine.scene;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModCelestialScenes {
    public static final ResourceLocation SUN = id("sun"), EARTH = id("earth");
    public static final PrototypeType<CelestialSceneData> TYPE = new PrototypeType<>(id("celestial_scene"),
            "moonstation14/celestial_scene", CelestialSceneData.CODEC);
    private ModCelestialScenes() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<CelestialSceneData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientCelestialScenes() : PrototypeRuntime.serverCelestialScenes();
    }
}

