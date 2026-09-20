package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData;
import com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record ReagentData(
        String id,
        String name,
        String group,
        String desc,
        String physicalDesc,
        String flavor,
        int color,
        Map<String, ReactiveEffectsData> reactiveEffects,
        float boilingPoint,
        float meltingPoint,
        ContrabandSeverityEnum contrabandSeverity,
        List<PlantMetabolismData> plantMetabolism,
        Map<String, MetabolismData> metabolisms,

        // Metamorphic stuff
        MetamorphicSpriteData metamorphicSpriteData,
        float metamorphicMaxFillLevels,
        String metamorphicFillBaseName,
        boolean metamorphicChangeColor,

        float pricePerUnit
) {
    // 1. First 8 fields
    private static final MapCodec<List<Object>> PART_A = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(o -> (String) o.get(0)),
            Codec.STRING.optionalFieldOf("name", "Unknown").forGetter(o -> (String) o.get(1)),
            Codec.STRING.optionalFieldOf("group", "unknown").forGetter(o -> (String) o.get(2)),
            Codec.STRING.optionalFieldOf("desc", "reagent-desc-default").forGetter(o -> (String) o.get(3)),
            Codec.STRING.optionalFieldOf("physicaldesc", "reagent-physical-desc-default").forGetter(o -> (String) o.get(4)),
            Codec.STRING.optionalFieldOf("flavor", "unknown").forGetter(o -> (String) o.get(5)),
            Codec.STRING.xmap(Integer::decode, c -> "0x" + Integer.toHexString(c).toUpperCase()).optionalFieldOf("color", 0x222222).forGetter(o -> (Integer) o.get(6)),
            Codec.unboundedMap(Codec.STRING, ReactiveEffectsData.CODEC).optionalFieldOf("reactiveeffects", Map.of()).forGetter(o -> (Map<String, ReactiveEffectsData>) o.get(7)),
            Codec.FLOAT.optionalFieldOf("boilingpoint", 400f).forGetter(o -> (Float) o.get(8))
    ).apply(instance, (a, b, c, d, e, f, g, h, i) -> Arrays.asList(a, b, c, d, e, f, g, h, i)));

    // 2. Remaining fields
    private static final MapCodec<List<Object>> PART_B = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("meltingpoint", 200f).forGetter(o -> (Float) o.get(0)),
            ContrabandSeverityEnum.CODEC.optionalFieldOf("contrabandseverity", ContrabandSeverityEnum.NONE).forGetter(o -> (ContrabandSeverityEnum) o.get(1)),
            Codec.list(PlantMetabolismData.CODEC).optionalFieldOf("plantmetabolism", List.of()).forGetter(o -> (List<PlantMetabolismData>) o.get(2)),
            Codec.unboundedMap(Codec.STRING, MetabolismData.CODEC).optionalFieldOf("metabolisms", Map.of("bloodstream", MetabolismData.DEFAULT)).forGetter(o -> (Map<String, MetabolismData>) o.get(3)),
            MetamorphicSpriteData.CODEC.optionalFieldOf("metamorphicsprite", new MetamorphicSpriteData(null, null)).forGetter(o -> (MetamorphicSpriteData) o.get(4)),
            Codec.FLOAT.optionalFieldOf("metamorphicmaxfilllevels", 0f).forGetter(o -> (Float) o.get(5)),
            Codec.STRING.optionalFieldOf("metamorphicfillbasename", "fill-").forGetter(o -> (String) o.get(6)),
            Codec.BOOL.optionalFieldOf("metamorphicchangecolor", false).forGetter(o -> (Boolean) o.get(7)),
            Codec.FLOAT.optionalFieldOf("priceperunit", 0f).forGetter(o -> (Float) o.get(8))
    ).apply(instance, (a, b, c, d, e, f, g, h, i) -> Arrays.asList(a, b, c, d, e, f, g, h, i)));

    // 3. The Combined Codec
    public static final Codec<ReagentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PART_A.forGetter(rd -> Arrays.asList(rd.id, rd.name, rd.group, rd.desc, rd.physicalDesc, rd.flavor, rd.color, rd.reactiveEffects, rd.boilingPoint)),
            PART_B.forGetter(rd -> Arrays.asList(rd.meltingPoint, rd.contrabandSeverity, rd.plantMetabolism, rd.metabolisms, rd.metamorphicSpriteData, rd.metamorphicMaxFillLevels, rd.metamorphicFillBaseName, rd.metamorphicChangeColor, rd.pricePerUnit))
    ).apply(instance, (a, b) -> new ReagentData(
            (String)a.get(0), (String)a.get(1), (String)a.get(2), (String)a.get(3), (String)a.get(4), (String)a.get(5), (Integer)a.get(6), (Map<String, ReactiveEffectsData>)a.get(7), (Float)a.get(8),
            (Float)b.get(0), (ContrabandSeverityEnum)b.get(1), (List<PlantMetabolismData>)b.get(2), (Map<String, MetabolismData>)b.get(3), (MetamorphicSpriteData)b.get(4), (Float)b.get(5), (String)b.get(6), (Boolean)b.get(7), (Float)b.get(8)
    )));
}
