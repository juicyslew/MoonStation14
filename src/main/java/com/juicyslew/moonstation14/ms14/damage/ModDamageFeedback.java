package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import java.util.Map;

public final class ModDamageFeedback {
    public static final PrototypeType<DamageFeedbackProfile> TYPE = new PrototypeType<>(id("damage_feedback"),
            "moonstation14/damage_feedback", DamageFeedbackProfile.CODEC);
    public static final ResourceLocation QUIET = id("quiet");
    public static final ResourceLocation PHYSICAL = id("physical");
    private ModDamageFeedback() { }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<DamageFeedbackProfile> serverCatalog() { return PrototypeRuntime.serverDamageFeedback(); }
    public static PrototypeCatalog<DamageFeedbackProfile> clientCatalog() { return PrototypeRuntime.clientDamageFeedback(); }
    public static DamageFeedbackProfile server(ResourceLocation id) { return require(serverCatalog(), id); }
    public static DamageFeedbackProfile client(ResourceLocation id) { return require(clientCatalog(), id); }
    public static DamageFeedbackProfile resolve(PrototypeCatalog<DamageFeedbackProfile> catalog, ResourceLocation id) {
        return require(catalog, id);
    }
    /** Reject candidate reloads that omit a profile consumed by a registered semantic damage cause. */
    public static void validateCandidate(Map<ResourceLocation, Map<ResourceLocation, JsonObject>> catalogs) {
        Map<ResourceLocation, JsonObject> profiles = catalogs.get(TYPE.typeId());
        if (profiles == null) throw new IllegalStateException("Missing damage feedback catalog " + TYPE.typeId());
        for (DamageSystem.Cause cause : DamageSystem.Cause.values()) {
            if (!profiles.containsKey(cause.profileId()))
                throw new IllegalStateException("Missing required damage feedback profile " + cause.profileId());
        }
    }
    private static DamageFeedbackProfile require(PrototypeCatalog<DamageFeedbackProfile> catalog, ResourceLocation id) {
        DamageFeedbackProfile value = catalog.get(id);
        if (value == null) throw new IllegalStateException("Missing resolved damage feedback profile " + id);
        return value;
    }
}

