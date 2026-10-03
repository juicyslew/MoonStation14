package com.juicyslew.moonstation14.ms14.explosion.prototype;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Registration and side-aware catalog access for explosion behavior prototypes. */
public final class ModExplosions {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "default");
    public static final PrototypeType<ExplosionData> EXPLOSION_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "explosion"),
            "moonstation14/explosion", ExplosionData.CODEC);

    private ModExplosions() { }

    public static PrototypeCatalog<ExplosionData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientExplosions() : PrototypeRuntime.serverExplosions();
    }

    public static ExplosionData require(PrototypeCatalog<ExplosionData> catalog, ResourceLocation id) {
        ExplosionData data = Objects.requireNonNull(catalog, "catalog").get(Objects.requireNonNull(id, "id"));
        if (data == null) throw new IllegalArgumentException("Missing explosion '" + id + "' in resolved prototype catalog");
        return data;
    }
}
