package com.juicyslew.moonstation14.ms14.equipment;
import com.google.gson.*;import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy;import net.minecraft.resources.ResourceLocation;import java.util.List;
public final class EquipmentItemMergeStrategy implements PrototypeMergeStrategy{
  @Override public boolean handles(String f){return List.of("item","clothing","allow_suit_storage","tags","armor","no_slip","protected_from_step_triggers","damage_on_interact_protection").contains(f);}
 @Override public JsonElement merge(String f,ResourceLocation id,JsonObject child,List<JsonObject> parents){JsonElement inherited=null;JsonObject out=new JsonObject();for(var p:parents)if(p.has(f)){if(p.get(f).isJsonObject())overlay(out,p.getAsJsonObject(f));else inherited=p.get(f).deepCopy();}if(child.has(f)){var v=child.get(f);if(v.isJsonObject())overlay(out,v.getAsJsonObject());else return v.deepCopy();}if(out.size()==0&&inherited!=null)return inherited;return out;}
 private static void overlay(JsonObject a,JsonObject b){b.entrySet().forEach(e->{if(e.getValue().isJsonObject()&&a.has(e.getKey())&&a.get(e.getKey()).isJsonObject()){JsonObject n=a.getAsJsonObject(e.getKey()).deepCopy();overlay(n,e.getValue().getAsJsonObject());a.add(e.getKey(),n);}else a.add(e.getKey(),e.getValue().deepCopy());});}
}
