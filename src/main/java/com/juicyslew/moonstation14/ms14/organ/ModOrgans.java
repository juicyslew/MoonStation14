package com.juicyslew.moonstation14.ms14.organ;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Objects;

public final class ModOrgans {
    public static final PrototypeType<OrganData> ORGAN_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "organ"),
            "moonstation14/organ", OrganData.CODEC);

    private ModOrgans() { }

    public static PrototypeCatalog<OrganData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientOrgans() : PrototypeRuntime.serverOrgans();
    }

    public static OrganData require(PrototypeCatalog<OrganData> catalog, ResourceLocation id) {
        OrganData organ = Objects.requireNonNull(catalog, "catalog").get(Objects.requireNonNull(id, "id"));
        if (organ == null) throw new IllegalArgumentException("Missing organ '" + id + "' in resolved prototype catalog");
        return organ;
    }
}
