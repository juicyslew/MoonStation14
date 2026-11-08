package com.juicyslew.moonstation14.ms14.jug;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModJugs {
    public static final ResourceLocation BASE_ID = id("jug_base");
    public static final ResourceLocation JUG_ID = id("jug");
    public static final PrototypeType<JugData> TYPE = new PrototypeType<>(id("jug"), "moonstation14/jug", JugData.CODEC, new JugMergeStrategy());
    private ModJugs() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<JugData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientJugs() : PrototypeRuntime.serverJugs();
    }
    public static JugData resolved(Level level) { return level == null ? null : catalog(level).get(JUG_ID); }
    public static JugData serverResolved() { return PrototypeRuntime.serverJugs().get(JUG_ID); }
}

