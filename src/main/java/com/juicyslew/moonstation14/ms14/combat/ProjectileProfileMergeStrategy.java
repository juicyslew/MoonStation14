package com.juicyslew.moonstation14.ms14.combat;
import com.google.gson.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
public final class ProjectileProfileMergeStrategy implements PrototypeMergeStrategy {
  @Override public boolean handles(String f){return f.equals("damage")||f.equals("ignore_resistances")||f.equals("delete_on_collide")||f.equals("lifetime_seconds")||f.equals("sprite")||f.equals("muzzle_flash")||f.equals("impact_effect")||f.equals("hit_sound")||f.equals("fly_by_sound");}
  @Override public JsonElement merge(String f,ResourceLocation id,JsonObject child,List<JsonObject> parents){if(!handles(f))return null; JsonElement v=null; for(JsonObject p:parents)if(p.has(f))v=p.get(f).deepCopy(); if(child.has(f)){JsonElement next=child.get(f).deepCopy();if((f.equals("impact_effect")||f.equals("hit_sound")||f.equals("fly_by_sound"))&&v!=null&&v.isJsonObject()&&next.isJsonObject())next=deepMerge(v.getAsJsonObject(),next.getAsJsonObject());v=next;} return v;}
 private static JsonObject deepMerge(JsonObject parent,JsonObject child){JsonObject out=parent.deepCopy();for(var e:child.entrySet()){JsonElement old=out.get(e.getKey()),value=e.getValue();out.add(e.getKey(),old!=null&&old.isJsonObject()&&value.isJsonObject()?deepMerge(old.getAsJsonObject(),value.getAsJsonObject()):value.deepCopy());}return out;}
}
