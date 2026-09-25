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

import java.util.Objects;

/** Prototype descriptor and side-aware accessors for character policies. */
public final class ModCharacters {
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
                        for (ResourceLocation id : resolved.keys()) {
                            JsonObject json = resolved.get(id);
                            CharacterSchemaAudit.audit(id, json);
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
}
