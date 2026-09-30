package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeLoadException;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.organ.ModOrgans;
import com.juicyslew.moonstation14.ms14.organ.OrganData;
import com.juicyslew.moonstation14.ms14.character.components.InitialBodyComponent;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
            new CharacterComponentMergeStrategy(),
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeJsonCatalogValidator)
                     (owned, resolved) -> {
                           for (ResourceLocation id : owned.keys()) {
                               try {
                                   CharacterSchemaAudit.rejectLegacyMovement(owned.get(id));
                                   CharacterSchemaAudit.rejectLegacyHands(owned.get(id));
                                  CharacterSchemaAudit.rejectLegacySlip(owned.get(id));
                                   CharacterSchemaAudit.rejectLegacyBlood(owned.get(id));
                                   CharacterSchemaAudit.rejectLegacyLungs(owned.get(id));
                                   CharacterSchemaAudit.rejectLegacyThermal(owned.get(id));
                             } catch (IllegalArgumentException exception) {
                                 throw new IllegalArgumentException("Character " + id + ": " + exception.getMessage(), exception);
                             }
                         }
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

    /** Validates blood reagent references against the same detached candidate that will be published. */
    public static void validateReagentReferences(
            Map<ResourceLocation, Map<ResourceLocation, JsonObject>> encodedCatalogs) {
        Map<ResourceLocation, JsonObject> reagents = encodedCatalogs.getOrDefault(
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(), Map.of());
        Set<ResourceLocation> reagentIds = Set.copyOf(reagents.keySet());
        Map<ResourceLocation, JsonObject> characters = encodedCatalogs.getOrDefault(CHARACTER_TYPE.typeId(), Map.of());
        for (Map.Entry<ResourceLocation, JsonObject> entry : characters.entrySet()) {
            ResourceLocation characterId = entry.getKey();
            var components = entry.getValue().getAsJsonArray("components");
            if (components == null) continue;
            for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
                JsonObject blood = components.get(componentIndex).getAsJsonObject();
                if (!"Bloodstream".equals(blood.get("type").getAsString())) continue;
                String path = "$.components[" + componentIndex + "]";

                JsonObject solution = blood.getAsJsonObject("reference_solution");
                if (solution != null) {
                    for (String id : solution.keySet()) {
                        requireReagent(characterId, path + ".reference_solution." + id, id, reagentIds);
                    }
                }

                if (blood.has("metabolism_exclusions") && blood.get("metabolism_exclusions").isJsonArray()) {
                    var exclusions = blood.getAsJsonArray("metabolism_exclusions");
                    for (int i = 0; i < exclusions.size(); i++) {
                        String id = exclusions.get(i).getAsString();
                        requireReagent(characterId, path + ".metabolism_exclusions[" + i + "]", id, reagentIds);
                    }
                }
            }
        }
    }

    /** Check resolved candidate references against the same candidate organ catalog, never the live snapshot. */
    public static void validateOrganReferences(Map<ResourceLocation, PrototypeCatalog<?>> catalogs) {
        PrototypeCatalog<?> characterCatalog = catalogs.get(CHARACTER_TYPE.typeId());
        PrototypeCatalog<?> organCatalog = catalogs.get(ModOrgans.ORGAN_TYPE.typeId());
        // Standalone character-only manager fixtures cannot resolve organ links; production registers both.
        if (characterCatalog == null || organCatalog == null) return;
        for (Map.Entry<ResourceLocation, ?> entry : characterCatalog.asMap().entrySet()) {
            CharacterData character = (CharacterData) entry.getValue();
            ResourceLocation characterId = entry.getKey();
            character.component(InitialBodyComponent.class).ifPresent(initial -> initial.organs().forEach((category, organId) -> {
                OrganData organ = organCatalog == null ? null : (OrganData) organCatalog.get(organId);
                if (organ == null || organ.category() != category) {
                    String path = "data/" + characterId.getNamespace() + "/" + CHARACTER_TYPE.resourceDirectory()
                            + "/" + characterId.getPath() + ".json";
                    throw new PrototypeLoadException(CHARACTER_TYPE.typeId(), characterId, path,
                            "$.components[InitialBody].organs." + category.serialized() + ": "
                                    + (organ == null ? "missing organ prototype '" : "wrong-category organ prototype '")
                                    + organId + "'");
                }
            }));
        }
    }

    private static void requireReagent(ResourceLocation characterId, String fieldPath, String reagentId,
                                       Set<ResourceLocation> reagentIds) {
        ResourceLocation parsed = ResourceLocation.tryParse(reagentId);
        if (parsed != null && reagentIds.contains(parsed)) return;
        String resourcePath = "data/" + characterId.getNamespace() + "/" + CHARACTER_TYPE.resourceDirectory()
                + "/" + characterId.getPath() + ".json";
        throw new PrototypeLoadException(CHARACTER_TYPE.typeId(), characterId, resourcePath,
                fieldPath + ": missing reagent prototype '" + reagentId + "'");
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
