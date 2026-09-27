package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/** Prototype descriptor and side-aware accessors for character policies. */
public final class ModCharacters {
    private static final Map<PrototypeCatalog<CharacterData>, Map<ResourceLocation, ResourceLocation>> HOST_INDEXES =
            new WeakHashMap<>();
    public static final ResourceKey<Registry<CharacterData>> CHARACTER_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "character"));

    public static final ResourceLocation HUMAN_ID =
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "human");

    public static final PrototypeType<CharacterData> CHARACTER_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "character"),
            "moonstation14/character",
            CharacterData.CODEC,
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeJsonCatalogValidator)
                    (owned, resolved) -> {
                        Map<ResourceLocation, ResourceLocation> hosts = new LinkedHashMap<>();
                        for (ResourceLocation id : resolved.keys()) {
                            JsonObject json = resolved.get(id);
                            CharacterSchemaAudit.audit(id, json);
                            for (ResourceLocation host : CharacterData.CODEC.parse(
                                    com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow().hostEntityTypes()) {
                                ResourceLocation previous = hosts.putIfAbsent(host, id);
                                if (previous != null) {
                                    throw new IllegalArgumentException("host entity type '" + host
                                            + "' is claimed by character prototypes '" + previous + "' and '" + id + "'");
                                }
                            }
                        }
                    });

    private ModCharacters() {
    }

    public static PrototypeCatalog<CharacterData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientCharacters() : PrototypeRuntime.serverCharacters();
    }

    public static CharacterData require(PrototypeCatalog<CharacterData> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "resolved character catalog");
        Objects.requireNonNull(id, "character id");
        CharacterData character = catalog.get(id);
        if (character == null) {
            throw new IllegalArgumentException("Missing character '" + id + "' in resolved prototype catalog");
        }
        return character;
    }

    public static CharacterData require(Level level, ResourceLocation id) {
        Objects.requireNonNull(level, "level");
        return require(catalog(level), id);
    }

    /** Returns the character prototype bound to this host type, if any. The index is scoped to this immutable snapshot. */
    public static Optional<ResourceLocation> characterForHost(Level level, ResourceLocation hostType) {
        Objects.requireNonNull(level, "level");
        return characterForHost(catalog(level), hostType);
    }

    /** Catalog overload supports callers already holding a stable prototype snapshot. */
    public static Optional<ResourceLocation> characterForHost(PrototypeCatalog<CharacterData> catalog,
                                                               ResourceLocation hostType) {
        Objects.requireNonNull(catalog, "resolved character catalog");
        Objects.requireNonNull(hostType, "host entity type");
        Map<ResourceLocation, ResourceLocation> index;
        synchronized (HOST_INDEXES) {
            index = HOST_INDEXES.computeIfAbsent(catalog, ModCharacters::buildHostIndex);
        }
        return Optional.ofNullable(index.get(hostType));
    }

    private static Map<ResourceLocation, ResourceLocation> buildHostIndex(PrototypeCatalog<CharacterData> catalog) {
        Map<ResourceLocation, ResourceLocation> index = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, CharacterData> entry : catalog.asMap().entrySet()) {
            for (ResourceLocation host : entry.getValue().hostEntityTypes()) {
                ResourceLocation previous = index.putIfAbsent(host, entry.getKey());
                if (previous != null) {
                    throw new IllegalArgumentException("host entity type '" + host
                            + "' is claimed by character prototypes '" + previous + "' and '" + entry.getKey() + "'");
                }
            }
        }
        return Map.copyOf(index);
    }
}
