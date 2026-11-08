package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Access to fixed explosive source prototypes. */
public final class ModExplosiveSources {
    public static final ResourceLocation EX_GRENADE_ID = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "ex_grenade");
    public static final PrototypeType<ExplosiveSourceData> EXPLOSIVE_SOURCE_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "explosive_source"),
            "moonstation14/explosive_source", ExplosiveSourceData.CODEC);

    private ModExplosiveSources() { }

    public static PrototypeCatalog<ExplosiveSourceData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientExplosiveSources() : PrototypeRuntime.serverExplosiveSources();
    }

    public static ExplosiveSourceData require(PrototypeCatalog<ExplosiveSourceData> catalog, ResourceLocation id) {
        ExplosiveSourceData data = Objects.requireNonNull(catalog, "catalog").get(Objects.requireNonNull(id, "id"));
        if (data == null) throw new IllegalArgumentException("Missing explosive source '" + id + "' in resolved prototype catalog");
        return data;
    }
}

