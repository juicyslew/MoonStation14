package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.DataResult;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record MetabolismData(List<EffectData> effects, Map<ResourceKey<ReagentData>, Float> metabolites, float rate) {

    public static final float DEFAULT_RATE = 0.5f;
    public static final Map<ResourceKey<ReagentData>, Float> DEFAULT_METABOLITES = Map.of();
    public static final List<EffectData> DEFAULT_EFFECTS = List.of();
    public static final MetabolismData DEFAULT = new MetabolismData(DEFAULT_EFFECTS, DEFAULT_METABOLITES, DEFAULT_RATE);

    private static final Codec<Float> RATIO_CODEC = Codec.FLOAT.validate(MetabolismData::validateRatioResult);
    private static final Codec<Float> RATE_CODEC = Codec.FLOAT.validate(MetabolismData::validateRateResult);

    public MetabolismData {
        if (effects == null) throw new NullPointerException("effects");
        if (metabolites == null) throw new NullPointerException("metabolites");
        effects = List.copyOf(effects);
        metabolites = Map.copyOf(metabolites);
        validateRate(rate);
        for (Map.Entry<ResourceKey<ReagentData>, Float> entry : metabolites.entrySet()) {
            if (entry.getKey() == null) throw new NullPointerException("metabolite key");
            validateRatio(entry.getValue());
        }
    }

    public static final Codec<MetabolismData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(EffectData.CODEC).optionalFieldOf("effects", DEFAULT_EFFECTS).forGetter(MetabolismData::effects),
            Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                    rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                    ResourceKey::location
            ), RATIO_CODEC).optionalFieldOf("metabolites", DEFAULT_METABOLITES).forGetter(MetabolismData::metabolites),
            RATE_CODEC.optionalFieldOf("metabolismrate", DEFAULT_RATE).forGetter(MetabolismData::rate)
    ).apply(inst, MetabolismData::new));

    private static DataResult<Float> validateRateResult(Float value) {
        try {
            validateRate(value);
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<Float> validateRatioResult(Float value) {
        try {
            validateRatio(value);
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validateRate(float value) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException("rate must be finite and strictly positive");
        }
    }

    private static void validateRatio(Float value) {
        if (value == null || !Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException("metabolite ratio must be finite and nonnegative");
        }
    }
}
