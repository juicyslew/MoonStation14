package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;

/** Data-driven loose object profile prototypes. */
public final class ModLooseObjectProfiles {
    public static final ResourceLocation GENERIC_ID = id("generic");
    public static final ResourceLocation JUG_ID = id("jug");
    public static final ResourceLocation GRENADE_ID = id("grenade");
    public static final PrototypeType<LooseObjectProfile> PROFILE_TYPE = new PrototypeType<>(
            id("loose_object_profile"), "moonstation14/loose_object_profile", LooseObjectProfile.CODEC,
            new com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy() {
                @Override public boolean handles(String fieldName) { return fieldName.equals("item_damage") || fieldName.equals("slippery") || fieldName.equals("step_trigger") || fieldName.equals("damage_on_interact"); }
                @Override public JsonElement merge(String fieldName, ResourceLocation childId, JsonObject child,
                                                   java.util.List<JsonObject> parents) {
                    JsonObject merged = new JsonObject();
                    for (JsonObject parent : parents) if (parent.has(fieldName) && parent.get(fieldName).isJsonObject()) {
                        parent.getAsJsonObject(fieldName).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
                        break;
                    }
                    if (child.has(fieldName) && child.get(fieldName).isJsonObject()) {
                        JsonObject childFields = child.getAsJsonObject(fieldName);
                      if (fieldName.equals("slippery") || fieldName.equals("item_damage") || fieldName.equals("step_trigger") || fieldName.equals("damage_on_interact")) for (var entry : childFields.entrySet()) {
                            if (entry.getValue().isJsonObject() && merged.has(entry.getKey()) && merged.get(entry.getKey()).isJsonObject()) {
                                JsonObject nested = merged.getAsJsonObject(entry.getKey()).deepCopy();
                                entry.getValue().getAsJsonObject().entrySet().forEach(e -> nested.add(e.getKey(), e.getValue().deepCopy()));
                                merged.add(entry.getKey(), nested);
                            } else merged.add(entry.getKey(), entry.getValue().deepCopy());
                        }
                        else childFields.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue().deepCopy()));
                    }
                    return merged.size() == 0 ? null : merged;
                }
            }, catalog -> catalog.asMap().forEach((profileId, profile) -> {
                var damage = profile.itemDamage();
                if (damage == null) return;
                for (var output : damage.destructionOutputs()) {
                    output.validate();
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(output.output());
                    if (item == null || item == net.minecraft.world.item.Items.AIR
                            || !output.output().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)))
                        throw new IllegalArgumentException("loose object profile '" + profileId
                                + "' has unknown destruction output: " + output.output());
                }
            }));

    private ModLooseObjectProfiles() { }
    public static void validateResolvedDestructionExplosions(java.util.Map<ResourceLocation, PrototypeCatalog<?>> catalogs) {
        var profileCatalog = catalogs.get(PROFILE_TYPE.typeId());
        if (profileCatalog == null) return;
        var explosionCatalog = catalogs.get(com.juicyslew.moonstation14.ms14.explosion.prototype.ModExplosions.EXPLOSION_TYPE.typeId());
        for (var entry : profileCatalog.asMap().entrySet()) {
            var damage = ((LooseObjectProfile) entry.getValue()).itemDamage();
            if (damage == null || damage.destructionExplosion() == null) continue;
            if (explosionCatalog == null || explosionCatalog.get(damage.destructionExplosion().explosionType()) == null)
                throw new IllegalArgumentException("loose object profile '" + entry.getKey() + "' has unknown destruction explosion: " + damage.destructionExplosion().explosionType());
        }
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }

    public static PrototypeCatalog<LooseObjectProfile> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientLooseObjectProfiles() : PrototypeRuntime.serverLooseObjectProfiles();
    }

    public static LooseObjectProfile require(PrototypeCatalog<LooseObjectProfile> catalog, ResourceLocation id) {
        Objects.requireNonNull(catalog, "catalog");
        if (id == null) id = GENERIC_ID;
        LooseObjectProfile profile = catalog.get(id);
        if (profile == null) throw new IllegalArgumentException("Missing loose object profile '" + id + "' in resolved prototype catalog");
        return profile;
    }
}
