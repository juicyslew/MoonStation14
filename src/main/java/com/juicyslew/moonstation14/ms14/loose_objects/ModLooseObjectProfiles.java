package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Data-driven loose object profile prototypes. */
public final class ModLooseObjectProfiles {
    public static final ResourceLocation GENERIC_ID = id("generic");
    public static final ResourceLocation JUG_ID = id("jug");
    public static final ResourceLocation GRENADE_ID = id("grenade");
    public static final PrototypeType<LooseObjectProfile> PROFILE_TYPE = new PrototypeType<>(
            id("loose_object_profile"), "moonstation14/loose_object_profile", LooseObjectProfile.CODEC);

    private ModLooseObjectProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }

    public static PrototypeCatalog<LooseObjectProfile> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientLooseObjectProfiles() : PrototypeRuntime.serverLooseObjectProfiles();
    }

    public static LooseObjectProfile require(PrototypeCatalog<LooseObjectProfile> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "catalog");
        if (id == null) id = GENERIC_ID;
        LooseObjectProfile profile = catalog.get(id);
        if (profile == null) throw new IllegalArgumentException("Missing loose object profile '" + id + "' in resolved prototype catalog");
        return profile;
    }
}

