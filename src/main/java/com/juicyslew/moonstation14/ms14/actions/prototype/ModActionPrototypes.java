package com.juicyslew.moonstation14.ms14.actions.prototype;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Typed resolved action profile family. */
public final class ModActionPrototypes {
    public static final PrototypeType<ActionPrototype> TYPE = new PrototypeType<>(id("character_action"),
            "moonstation14/character_action", ActionPrototype.CODEC);
    private ModActionPrototypes() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    public static PrototypeCatalog<ActionPrototype> serverCatalog() { return com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager().snapshot(TYPE); }
    public static PrototypeCatalog<ActionPrototype> clientCatalog() { return com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientManager().snapshot(TYPE); }
}
