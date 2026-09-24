package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** Prototype descriptor and side-aware accessors for custom status effects. */
public final class ModStatusEffects {
    public static final ResourceKey<Registry<StatusEffectData>> STATUS_EFFECT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "status_effect"));

    public static final PrototypeType<StatusEffectData> STATUS_EFFECT_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "status_effect"),
            "moonstation14/status_effect",
            StatusEffectData.CODEC,
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeJsonCatalogValidator)
                    (owned, resolved) -> {
                        for (ResourceLocation id : resolved.keys()) {
                            JsonObject json = resolved.get(id);
                            StatusEffectSchemaAudit.audit(id, json);
                        }
                    });

    private ModStatusEffects() {
    }

    public static PrototypeCatalog<StatusEffectData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientStatusEffects() : PrototypeRuntime.serverStatusEffects();
    }

    public static StatusEffectData require(PrototypeCatalog<StatusEffectData> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "resolved status-effect catalog");
        Objects.requireNonNull(id, "status effect id");
        StatusEffectData statusEffect = catalog.get(id);
        if (statusEffect == null) {
            throw new IllegalArgumentException("Missing status effect '" + id
                    + "' in resolved prototype catalog");
        }
        return statusEffect;
    }

    public static StatusEffectData require(Level level, ResourceLocation id) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(id, "status effect id");
        PrototypeCatalog<StatusEffectData> catalog = catalog(level);
        StatusEffectData statusEffect = catalog.get(id);
        if (statusEffect == null) {
            String side = level.isClientSide() ? "client" : "server";
            throw new IllegalArgumentException("Missing status effect '" + id + "' in " + side
                    + " resolved prototype catalog");
        }
        return statusEffect;
    }

    public static ResourceKey<StatusEffectData> createKey(String id) {
        return ResourceKey.create(STATUS_EFFECT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, id.toLowerCase()));
    }
}
