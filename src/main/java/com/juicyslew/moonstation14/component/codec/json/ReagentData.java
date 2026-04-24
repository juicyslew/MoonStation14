package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record ReagentData(
        String id,
        String name,
        String group,
        String desc,
        String physicalDesc,
        String flavor,
        int color,
        Map<String, MetabolismData> metabolisms
) {
    public static final Codec<ReagentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ReagentData::id),
            Codec.STRING.fieldOf("name").forGetter(ReagentData::name),
            Codec.STRING.fieldOf("group").forGetter(ReagentData::group),
            Codec.STRING.fieldOf("desc").forGetter(ReagentData::desc),
            Codec.STRING.fieldOf("physicalDesc").forGetter(ReagentData::physicalDesc),
            Codec.STRING.fieldOf("flavor").forGetter(ReagentData::flavor),
            Codec.STRING.fieldOf("color").xmap(Integer::decode, i -> "N/A").forGetter(ReagentData::color),
            Codec.unboundedMap(Codec.STRING, MetabolismData.CODEC).optionalFieldOf("metabolisms", Map.of()).forGetter(ReagentData::metabolisms)
    ).apply(instance, ReagentData::new));
}

// Metabolism holds effects list


// Polymorphic Effect


//// DamageSpec mirrors the nested "damage" object in your JSON example
/// CONSIDER REPLACING EXISTING DAMAGE DICTIONARY
//public record DamageSpec(
//        // If the JSON uses "damage": {"Brute": -1.5} or nested "types": {...}
//        Map<String, Double> direct,
//        Map<String, Double> types
//) {
//    public static final Codec<DamageSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
//            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("damage", Map.of()).forGetter(d -> d.direct),
//            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("types", Map.of()).forGetter(d -> d.types)
//    ).apply(inst, (direct, types) -> new DamageSpec(direct, types)));
//}
