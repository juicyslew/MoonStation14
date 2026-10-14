package com.juicyslew.moonstation14.ms14.tools;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Resolved local composition for supported tool and prying components. */
public final class ModToolProfiles {
    public static final ResourceLocation BASE_ITEM = id("base_item");
    public static final ResourceLocation BASE_CROWBAR = id("base_crowbar");
    public static final ResourceLocation CROWBAR = id("crowbar");
    public static final ResourceLocation WIRECUTTER = id("wirecutter");
    public static final ResourceLocation SCREWDRIVER = id("screwdriver");
    public static final ResourceLocation MULTITOOL = id("multitool");
    public static final ResourceLocation BOTANY_HOE = id("botany_hoe");
    public static final ResourceLocation BOTANY_SPADE = id("botany_spade");
    public static final PrototypeType<ToolProfile> TYPE = new PrototypeType<>(id("tool_profile"),
            "moonstation14/tool_profile", ToolProfile.CODEC,
            ModToolProfiles::validateInheritance, ModToolProfiles::validateCatalog);

    private ModToolProfiles() { }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }

    private static void validateCatalog(PrototypeCatalog<ToolProfile> catalog) {
        for (ToolProfile profile : catalog.asMap().values()) profile.validate();
        for (ResourceLocation item : Set.of(CROWBAR, WIRECUTTER, SCREWDRIVER, MULTITOOL)) {
            ToolProfile profile = catalog.get(item);
            if (profile == null || profile.tool() == null || profile.tool().qualities().isEmpty())
                throw new IllegalArgumentException("missing concrete tool profile: " + item);
        }
        ToolProfile crowbar = catalog.get(CROWBAR);
        if (crowbar.prying() == null) throw new IllegalArgumentException("crowbar must inherit prying");
    }

    private static void validateInheritance(PrototypeCatalog<JsonObject> raw,
                                            PrototypeCatalog<JsonObject> resolved) {
        requireAbstract(raw.get(BASE_ITEM), BASE_ITEM, Set.of("abstract", "tool"));
        requireAbstract(raw.get(BASE_CROWBAR), BASE_CROWBAR, Set.of("abstract", "parent", "prying"));
        if (!BASE_ITEM.getPath().equals(raw.get(BASE_CROWBAR).get("parent").getAsString()))
            throw new IllegalArgumentException("base_crowbar must inherit base_item");
        for (ResourceLocation id : Set.of(CROWBAR, WIRECUTTER, SCREWDRIVER, MULTITOOL)) {
            JsonObject child = raw.get(id);
            if (child == null || child.has("abstract") || !child.has("tool") || child.has("prying"))
                throw new IllegalArgumentException(id + " must define its tool quality and inherit prying where applicable");
            String expected = id.equals(CROWBAR) ? BASE_CROWBAR.getPath() : BASE_ITEM.getPath();
            if (!child.has("parent") || !expected.equals(child.get("parent").getAsString()))
                throw new IllegalArgumentException(id + " has an invalid profile parent");
            if (child.has("metadata")) {
                if (!child.get("metadata").isJsonObject()) throw new IllegalArgumentException("invalid tool metadata");
                for (String field : child.getAsJsonObject("metadata").keySet())
                    if (!Set.of("name", "description").contains(field)) throw new IllegalArgumentException("unknown tool metadata field: " + field);
            }
        }
        if (resolved.asMap().containsKey(BASE_ITEM) || resolved.asMap().containsKey(BASE_CROWBAR))
            throw new IllegalArgumentException("abstract tool profiles must not be published");
    }

    private static void requireAbstract(JsonObject json, ResourceLocation id, Set<String> fields) {
        if (json == null || !json.has("abstract") || !json.get("abstract").getAsBoolean()
                || !json.keySet().equals(fields)) throw new IllegalArgumentException("invalid abstract profile " + id);
    }
}

