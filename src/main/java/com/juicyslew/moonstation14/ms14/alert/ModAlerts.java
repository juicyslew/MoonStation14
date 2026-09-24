package com.juicyslew.moonstation14.ms14.alert;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.component.codec.json.AlertSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.Objects;

/** Prototype descriptor and side-aware accessors for custom alerts. */
public final class ModAlerts {
    private static final String DEFAULT_NAMESPACE = MoonStation14.MOD_ID;

    public static final ResourceKey<Registry<AlertData>> ALERT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "alert"));

    /** Strict alert reference codec; it accepts an unqualified canonical path. */
    public static final Codec<ResourceKey<AlertData>> ALERT_KEY_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(createKeyFromReference(value));
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? "malformed alert reference" : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            },
            value -> value.location().toString());

    public static final PrototypeType<AlertData> ALERT_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "alert"),
            "moonstation14/alert",
            AlertData.CODEC,
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeJsonCatalogValidator)
                    (owned, resolved) -> {
                        for (ResourceLocation id : resolved.keys()) {
                            AlertSchemaAudit.audit(id, resolved.get(id));
                        }
                    });

    private ModAlerts() {
    }

    public static PrototypeCatalog<AlertData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientAlerts() : PrototypeRuntime.serverAlerts();
    }

    public static AlertData require(PrototypeCatalog<AlertData> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "resolved alert catalog");
        Objects.requireNonNull(id, "alert id");
        AlertData alert = catalog.get(id);
        if (alert == null) {
            throw new IllegalArgumentException("Missing alert '" + id + "' in resolved prototype catalog");
        }
        return alert;
    }

    public static AlertData require(Level level, ResourceLocation id) {
        Objects.requireNonNull(level, "level");
        PrototypeCatalog<AlertData> catalog = catalog(level);
        AlertData alert = catalog.get(id);
        if (alert == null) {
            String side = level.isClientSide() ? "client" : "server";
            throw new IllegalArgumentException("Missing alert '" + id + "' in " + side
                    + " resolved prototype catalog");
        }
        return alert;
    }

    public static ResourceKey<AlertData> createKey(String path) {
        Objects.requireNonNull(path, "alert id");
        return ResourceKey.create(ALERT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, path.toLowerCase(Locale.ROOT)));
    }

    /** Resolves the serialized reference without forgiving case drift. */
    public static ResourceKey<AlertData> createKeyFromReference(String value) {
        Objects.requireNonNull(value, "alert reference");
        if (value.isBlank()) {
            throw new IllegalArgumentException("alert reference must not be blank");
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("non-canonical alert reference '" + value + "'");
        }
        ResourceLocation location;
        try {
            location = value.contains(":")
                    ? ResourceLocation.parse(value)
                    : ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("malformed alert reference '" + value + "'", exception);
        }
        String canonical = value.contains(":") ? location.toString() : location.getPath();
        if (!value.equals(canonical)) {
            throw new IllegalArgumentException("non-canonical alert reference '" + value + "'");
        }
        return ResourceKey.create(ALERT_REGISTRY_KEY, location);
    }

    public static ResourceLocation locationFromReference(String value) {
        return createKeyFromReference(value).location();
    }
}
