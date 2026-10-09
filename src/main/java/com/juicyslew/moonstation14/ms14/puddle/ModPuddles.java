package com.juicyslew.moonstation14.ms14.puddle;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModPuddles {
    public static final ResourceLocation PUDDLE = id("puddle");
    public static final PrototypeType<PuddleData> TYPE = new PrototypeType<>(id("puddle"), "moonstation14/puddle", PuddleData.CODEC);
    private ModPuddles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<PuddleData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientPuddles() : PrototypeRuntime.serverPuddles();
    }
}
