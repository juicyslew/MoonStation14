package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReagentReferenceValidator;
import com.juicyslew.moonstation14.component.codec.json.ReagentSchemaAudit;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class ModReagents {
    public static final ResourceKey<Registry<ReagentData>> REAGENT_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "reagent"));
    public static final PrototypeType<ReagentData> REAGENT_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "reagent"),
            "moonstation14/reagent",
            ReagentData.CODEC,
            new ReagentPrototypeMergeStrategy(),
            ModReagents::validatePrototypeCatalog);

    public static PrototypeCatalog<ReagentData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientReagents() : PrototypeRuntime.serverReagents();
    }

    public static ReagentData require(PrototypeCatalog<ReagentData> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "resolved reagent catalog");
        ReagentData reagent = catalog.get(id);
        if (reagent == null) {
            throw new IllegalArgumentException("Missing reagent '" + id + "' in resolved prototype catalog");
        }
        return reagent;
    }

    public static ReagentData require(Level level, ResourceLocation id) {
        Objects.requireNonNull(level, "level");
        PrototypeCatalog<ReagentData> catalog = catalog(level);
        ReagentData reagent = catalog.get(id);
        if (reagent == null) {
            String side = level.isClientSide() ? "client" : "server";
            throw new IllegalArgumentException("Missing reagent '" + id + "' in " + side
                    + " resolved prototype catalog");
        }
        return reagent;
    }

    public static ResourceKey<ReagentData> createKey(String path) {
        return ResourceKey.create(REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", path));
    }

    private static void validatePrototypeCatalog(PrototypeCatalog<JsonObject> ownedRaw,
                                                  PrototypeCatalog<JsonObject> resolved) {
        for (ResourceLocation id : resolved.keys()) {
            JsonObject reagent = resolved.get(id);
            ReagentSchemaAudit.audit(id, reagent);
            ReagentReferenceValidator.validate(id, reagent, ownedRaw.asMap());
        }
    }
}
