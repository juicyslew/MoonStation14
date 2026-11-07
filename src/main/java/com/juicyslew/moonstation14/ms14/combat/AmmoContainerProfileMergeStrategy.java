package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Fieldwise inherited defaults; arrays deliberately replace rather than concatenate. */
public final class AmmoContainerProfileMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String field) { return field.equals("capacity") || field.equals("accepted_tags") || field.equals("magazine_tags") || field.equals("fill_cartridge_prototype") || field.equals("insert_sound"); }
    @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
        if (!handles(field)) return null;
        JsonElement value = null;
        for (JsonObject parent : parents) if (parent.has(field)) value = parent.get(field).deepCopy();
        if (child.has(field)) value = child.get(field).deepCopy();
        return value;
    }
}
