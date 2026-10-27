package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Edits resolved fixture composition without creating duplicate component types. */
public final class CharacterPrototypeComponentUpsert {
    private CharacterPrototypeComponentUpsert() { }

    public static void upsert(JsonArray components, JsonObject replacement) {
        if (components == null || replacement == null || !replacement.has("type"))
            throw new IllegalArgumentException("component replacement requires a typed component");
        String type = replacement.get("type").getAsString();
        int found = -1;
        for (int i = 0; i < components.size(); i++) {
            JsonElement value = components.get(i);
            if (!value.isJsonObject() || !value.getAsJsonObject().has("type")) continue;
            if (!type.equals(value.getAsJsonObject().get("type").getAsString())) continue;
            if (found >= 0) throw new IllegalArgumentException("resolved fixture already duplicates component " + type);
            found = i;
        }
        if (found >= 0) components.set(found, replacement.deepCopy());
        else components.add(replacement.deepCopy());
    }
}
