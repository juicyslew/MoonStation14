package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.codec.json.metamorphic.MetamorphicSpriteData;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismStage;
import com.juicyslew.moonstation14.util.enums.ContrabandSeverityEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

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
        Map<MetabolismStage, MetabolismData> metabolisms,

        // Metamorphic stuff
        MetamorphicSpriteData metamorphicSpriteData,
        float metamorphicMaxFillLevels,
        String metamorphicFillBaseName,
        boolean metamorphicChangeColor,

        float pricePerUnit,

        // Optional top-level reagent properties. Presence is retained so codecs do not
        // invent defaults for fields that are absent from a prototype.
        Optional<Boolean> recognizable,
        Optional<Float> fizziness,
        Optional<Boolean> worksOnTheDead,
        Optional<List<String>> allowedDepartments,
        Optional<List<String>> allowedJobs,
        Optional<SlipData> slipData,
        Optional<Float> friction,
        Optional<List<TileReactionData>> tileReactions,
        Optional<FootstepSoundData> footstepSound,
        Optional<Boolean> standsOut,
        Optional<Float> flavorMinimum,
        Optional<Float> evaporationSpeed,
        Optional<Float> viscosity,
        Optional<Boolean> absorbent
) {
    public ReagentData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(desc, "desc");
        Objects.requireNonNull(physicalDesc, "physicalDesc");
        Objects.requireNonNull(flavor, "flavor");
        reactiveEffects = Map.copyOf(Objects.requireNonNull(reactiveEffects, "reactiveEffects"));
        plantMetabolism = List.copyOf(Objects.requireNonNull(plantMetabolism, "plantMetabolism"));
        metabolisms = Map.copyOf(Objects.requireNonNull(metabolisms, "metabolisms"));
        allowedDepartments = copyOptionalList(allowedDepartments);
        allowedJobs = copyOptionalList(allowedJobs);
        tileReactions = copyOptionalList(tileReactions);
    }

    /** Compatibility constructor for callers that only provide the original fields. */
    public ReagentData(String id, String name, String group, String desc, String physicalDesc,
                       String flavor, int color, Map<String, ReactiveEffectsData> reactiveEffects,
                       float boilingPoint, float meltingPoint, ContrabandSeverityEnum contrabandSeverity,
                        List<PlantMetabolismData> plantMetabolism, Map<MetabolismStage, MetabolismData> metabolisms,
                       MetamorphicSpriteData metamorphicSpriteData, float metamorphicMaxFillLevels,
                       String metamorphicFillBaseName, boolean metamorphicChangeColor, float pricePerUnit) {
        this(id, name, group, desc, physicalDesc, flavor, color, reactiveEffects, boilingPoint,
                meltingPoint, contrabandSeverity, plantMetabolism, metabolisms, metamorphicSpriteData,
                metamorphicMaxFillLevels, metamorphicFillBaseName, metamorphicChangeColor, pricePerUnit,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

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
             Codec.unboundedMap(MetabolismStage.CODEC, MetabolismData.CODEC).optionalFieldOf("metabolisms", Map.of()).forGetter(o -> (Map<MetabolismStage, MetabolismData>) o.get(3)),
            MetamorphicSpriteData.CODEC.optionalFieldOf("metamorphicsprite", new MetamorphicSpriteData(null, null)).forGetter(o -> (MetamorphicSpriteData) o.get(4)),
            Codec.FLOAT.optionalFieldOf("metamorphicmaxfilllevels", 0f).forGetter(o -> (Float) o.get(5)),
            Codec.STRING.optionalFieldOf("metamorphicfillbasename", "fill-").forGetter(o -> (String) o.get(6)),
            Codec.BOOL.optionalFieldOf("metamorphicchangecolor", false).forGetter(o -> (Boolean) o.get(7)),
            Codec.FLOAT.optionalFieldOf("priceperunit", 0f).forGetter(o -> (Float) o.get(8))
     ).apply(instance, (a, b, c, d, e, f, g, h, i) -> Arrays.asList(a, b, c, d, e, f, g, h, i)));

    // 3. Optional top-level properties retained without applying behavior defaults
    private static final MapCodec<List<Object>> PART_C = RecordCodecBuilder.<List<Object>>mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("recognizable").forGetter(o -> (Optional<Boolean>) o.get(0)),
            Codec.FLOAT.optionalFieldOf("fizziness").forGetter(o -> (Optional<Float>) o.get(1)),
            Codec.BOOL.optionalFieldOf("worksonthedead").forGetter(o -> (Optional<Boolean>) o.get(2)),
            Codec.list(Codec.STRING).optionalFieldOf("alloweddepartments").forGetter(o -> (Optional<List<String>>) o.get(3)),
            Codec.list(Codec.STRING).optionalFieldOf("allowedjobs").forGetter(o -> (Optional<List<String>>) o.get(4)),
            SlipData.CODEC.codec().optionalFieldOf("slipdata").forGetter(o -> (Optional<SlipData>) o.get(5)),
            Codec.FLOAT.optionalFieldOf("friction").forGetter(o -> (Optional<Float>) o.get(6)),
            Codec.list(TileReactionData.CODEC.codec()).optionalFieldOf("tilereactions").forGetter(o -> (Optional<List<TileReactionData>>) o.get(7)),
            FootstepSoundData.CODEC.codec().optionalFieldOf("footstepsound").forGetter(o -> (Optional<FootstepSoundData>) o.get(8)),
            Codec.BOOL.optionalFieldOf("standsout").forGetter(o -> (Optional<Boolean>) o.get(9)),
            Codec.FLOAT.optionalFieldOf("flavorminimum").forGetter(o -> (Optional<Float>) o.get(10)),
            Codec.FLOAT.optionalFieldOf("evaporationspeed").forGetter(o -> (Optional<Float>) o.get(11)),
            Codec.FLOAT.optionalFieldOf("viscosity").forGetter(o -> (Optional<Float>) o.get(12)),
            Codec.BOOL.optionalFieldOf("absorbent").forGetter(o -> (Optional<Boolean>) o.get(13))
    ).apply(instance, (a, b, c, d, e, f, g, h, i, j, k, l, m, n) ->
            Arrays.asList(a, b, c, d, e, f, g, h, i, j, k, l, m, n)));

    // 4. The Combined Codec
    public static final Codec<ReagentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PART_A.forGetter(rd -> Arrays.asList(rd.id, rd.name, rd.group, rd.desc, rd.physicalDesc, rd.flavor, rd.color, rd.reactiveEffects, rd.boilingPoint)),
            PART_B.forGetter(rd -> Arrays.asList(rd.meltingPoint, rd.contrabandSeverity, rd.plantMetabolism, rd.metabolisms, rd.metamorphicSpriteData, rd.metamorphicMaxFillLevels, rd.metamorphicFillBaseName, rd.metamorphicChangeColor, rd.pricePerUnit)),
            PART_C.forGetter(rd -> Arrays.asList(rd.recognizable, rd.fizziness, rd.worksOnTheDead, rd.allowedDepartments, rd.allowedJobs, rd.slipData, rd.friction, rd.tileReactions, rd.footstepSound, rd.standsOut, rd.flavorMinimum, rd.evaporationSpeed, rd.viscosity, rd.absorbent))
    ).apply(instance, (a, b, c) -> new ReagentData(
            (String)a.get(0), (String)a.get(1), (String)a.get(2), (String)a.get(3), (String)a.get(4), (String)a.get(5), (Integer)a.get(6), (Map<String, ReactiveEffectsData>)a.get(7), (Float)a.get(8),
            (Float)b.get(0), (ContrabandSeverityEnum)b.get(1), (List<PlantMetabolismData>)b.get(2), (Map<MetabolismStage, MetabolismData>)b.get(3), (MetamorphicSpriteData)b.get(4), (Float)b.get(5), (String)b.get(6), (Boolean)b.get(7), (Float)b.get(8),
            (Optional<Boolean>)c.get(0), (Optional<Float>)c.get(1), (Optional<Boolean>)c.get(2), (Optional<List<String>>)c.get(3), (Optional<List<String>>)c.get(4), (Optional<SlipData>)c.get(5), (Optional<Float>)c.get(6), (Optional<List<TileReactionData>>)c.get(7), (Optional<FootstepSoundData>)c.get(8), (Optional<Boolean>)c.get(9), (Optional<Float>)c.get(10), (Optional<Float>)c.get(11), (Optional<Float>)c.get(12), (Optional<Boolean>)c.get(13)
     )));

    private static <T> Optional<List<T>> copyOptionalList(Optional<List<T>> value) {
        Objects.requireNonNull(value, "optional list");
        return value.map(List::copyOf);
    }
}
