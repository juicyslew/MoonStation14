package com.juicyslew.moonstation14.ms14.combat;
import com.google.gson.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
/** Fieldwise merge for inherited cartridge composition. */
public final class CartridgeProfileMergeStrategy implements PrototypeMergeStrategy {
 @Override public boolean handles(String field) { return field.equals("projectile") || field.equals("delete_on_spawn") || field.equals("tags"); }
 @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, List<JsonObject> parents) {
  if (!handles(field)) return null;
  JsonElement value=null;
  for (JsonObject p:parents) if(p.has(field)) value=p.get(field).deepCopy();
  if(child.has(field)) value=child.get(field).deepCopy();
  return value;
 }
}
