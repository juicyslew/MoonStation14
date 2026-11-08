package com.juicyslew.moonstation14.ms14.equipment;
import com.google.gson.*; import com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy; import net.minecraft.resources.ResourceLocation; import java.util.List;
public final class InventoryTemplateMergeStrategy implements PrototypeMergeStrategy {
    @Override public boolean handles(String f){return f.equals("slots");}
    @Override public JsonElement merge(String f,ResourceLocation id,JsonObject child,List<JsonObject> parents){
        java.util.LinkedHashMap<String,JsonObject> merged=new java.util.LinkedHashMap<>();
        for(var p:parents) if(p.has(f)&&p.get(f).isJsonArray()) for(var element:p.getAsJsonArray(f)) if(element.isJsonObject()) mergeSlot(merged,element.getAsJsonObject());
        if(child.has(f)&&child.get(f).isJsonArray()) for(var element:child.getAsJsonArray(f)) if(element.isJsonObject()) mergeSlot(merged,element.getAsJsonObject());
        JsonArray out=new JsonArray();merged.values().forEach(out::add);return out;
    }
    private static void mergeSlot(java.util.Map<String,JsonObject> slots,JsonObject source){String key=source.get("id").getAsString();JsonObject target=slots.computeIfAbsent(key,k->new JsonObject());source.entrySet().forEach(e->target.add(e.getKey(),e.getValue().deepCopy()));}
}

