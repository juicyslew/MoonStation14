package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.effect.EffectContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

sealed interface PlantMetabolismData permits
        PlantMetabolismData.PlantAdjustNutrition,
        PlantMetabolismData.PlantAdjustWeeds,
        PlantMetabolismData.PlantAdjustPests,
        PlantMetabolismData.PlantAdjustHealth,
        PlantMetabolismData.PlantAdjustWater,
        PlantMetabolismData.PlantAdjustToxins,
        PlantMetabolismData.PlantCryoxadone,
        PlantMetabolismData.PlantAffectGrowth,
        PlantMetabolismData.PlantDiethylamine,
        PlantMetabolismData.PlantAdjustMutationMod,
        PlantMetabolismData.PlantMutateChemicals,
        PlantMetabolismData.PlantPhalanximine,
        PlantMetabolismData.PlantRemoveKudzu,
        PlantMetabolismData.RobustHarvest,
        PlantMetabolismData.PlantAdjustMutationLevel,
        PlantMetabolismData.PlantRestoreSeeds,
        PlantMetabolismData.PlantAdjustPotency
{
    String type();
    void apply(EffectContext ctx);

    Codec<PlantMetabolismData> CODEC = Codec.STRING.dispatch(
        "type",              // The JSON field name
        PlantMetabolismData::type,    // How to get the string from the object (for encoding)
        type -> switch (type) { // How to get the codec from the string (for decoding)
            case "PlantAdjustNutrition"             -> PlantMetabolismData.PlantAdjustNutrition.CODEC;
            case "PlantAdjustWeeds"                 -> PlantMetabolismData.PlantAdjustWeeds.CODEC;
            case "PlantAdjustPests"                 -> PlantMetabolismData.PlantAdjustPests.CODEC;
            case "PlantAdjustHealth"                -> PlantMetabolismData.PlantAdjustHealth.CODEC;
            case "PlantAdjustWater"                 -> PlantMetabolismData.PlantAdjustWater.CODEC;
            case "PlantAdjustToxins"                -> PlantMetabolismData.PlantAdjustToxins.CODEC;
            case "PlantCryoxadone"                  -> PlantMetabolismData.PlantCryoxadone.CODEC;
            case "PlantAffectGrowth"                -> PlantMetabolismData.PlantAffectGrowth.CODEC;
            case "PlantDiethylamine"                -> PlantMetabolismData.PlantDiethylamine.CODEC;
            case "PlantAdjustMutationMod"           -> PlantMetabolismData.PlantAdjustMutationMod.CODEC;
            case "PlantMutateChemicals"             -> PlantMetabolismData.PlantMutateChemicals.CODEC;
            case "PlantPhalanximine"                -> PlantMetabolismData.PlantPhalanximine.CODEC;
            case "PlantRemoveKudzu"                 -> PlantMetabolismData.PlantRemoveKudzu.CODEC;
            case "RobustHarvest"                    -> PlantMetabolismData.RobustHarvest.CODEC;
            case "PlantAdjustMutationLevel"         -> PlantMetabolismData.PlantAdjustMutationLevel.CODEC;
            case "PlantRestoreSeeds"                -> PlantMetabolismData.PlantRestoreSeeds.CODEC;
            case "PlantAdjustPotency"               -> PlantMetabolismData.PlantAdjustPotency.CODEC;

            default -> throw new IllegalStateException("Unknown PlantMetaabolism effect type: " + type);
        }
    );

    record PlantAdjustNutrition(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustNutrition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustNutrition::amount)
        ).apply(inst, PlantAdjustNutrition::new));

        @Override public String type() { return "PlantAdjustNutrition"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustWeeds(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustWeeds> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustWeeds::amount)
        ).apply(inst, PlantAdjustWeeds::new));

        @Override public String type() { return "PlantAdjustWeeds"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustPests(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustPests> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustPests::amount)
        ).apply(inst, PlantAdjustPests::new));

        @Override public String type() { return "PlantAdjustPests"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustHealth(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustHealth> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustHealth::amount)
        ).apply(inst, PlantAdjustHealth::new));

        @Override public String type() { return "PlantAdjustHealth"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustWater(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustWater> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustWater::amount)
        ).apply(inst, PlantAdjustWater::new));

        @Override public String type() { return "PlantAdjustWater"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustToxins(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustToxins> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustToxins::amount)
        ).apply(inst, PlantAdjustToxins::new));

        @Override public String type() { return "PlantAdjustToxins"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantCryoxadone() implements PlantMetabolismData {
        public static final MapCodec<PlantCryoxadone> CODEC = MapCodec.unit(new PlantCryoxadone());

        @Override public String type() { return "PlantCryoxadone"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }


    record PlantAffectGrowth(float probability, float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAffectGrowth> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("probability").forGetter(PlantAffectGrowth::probability),
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAffectGrowth::amount)
        ).apply(inst, PlantAffectGrowth::new));

        @Override public String type() { return "PlantAffectGrowth"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantDiethylamine() implements PlantMetabolismData {
        public static final MapCodec<PlantDiethylamine> CODEC = MapCodec.unit(new PlantDiethylamine());

        @Override public String type() { return "PlantDiethylamine"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustMutationMod(float probability, float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustMutationMod> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustMutationMod::probability),
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustMutationMod::amount)
        ).apply(inst, PlantAdjustMutationMod::new));

        @Override public String type() { return "PlantAdjustMutationMod"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantMutateChemicals(String randomPickBotanyReagent) implements PlantMetabolismData {
        public static final MapCodec<PlantMutateChemicals> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("randompickbotanyreagent").forGetter(PlantMutateChemicals::randomPickBotanyReagent)
        ).apply(inst, PlantMutateChemicals::new));

        @Override public String type() { return "PlantMutateChemicals"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantPhalanximine(float minScale) implements PlantMetabolismData {
        public static final MapCodec<PlantPhalanximine> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("minscale").forGetter(PlantPhalanximine::minScale)
        ).apply(inst, PlantPhalanximine::new));

        @Override public String type() { return "PlantPhalanximine"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantRemoveKudzu() implements PlantMetabolismData {
        public static final MapCodec<PlantRemoveKudzu> CODEC = MapCodec.unit(new PlantRemoveKudzu());

        @Override public String type() { return "PlantRemoveKudzu"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record RobustHarvest() implements PlantMetabolismData {
        public static final MapCodec<RobustHarvest> CODEC = MapCodec.unit(new RobustHarvest());

        @Override public String type() { return "RobustHarvest"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustMutationLevel(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustMutationLevel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustMutationLevel::amount)
        ).apply(inst, PlantAdjustMutationLevel::new));

        @Override public String type() { return "PlantAdjustMutationLevel"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantRestoreSeeds(float probability) implements PlantMetabolismData {
        public static final MapCodec<PlantRestoreSeeds> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("probability").forGetter(PlantRestoreSeeds::probability)
        ).apply(inst, PlantRestoreSeeds::new));

        @Override public String type() { return "PlantRestoreSeeds"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }

    record PlantAdjustPotency(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustPotency> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustPotency::amount)
        ).apply(inst, PlantAdjustPotency::new));

        @Override public String type() { return "PlantAdjustPotency"; }
        @Override public void apply(EffectContext ctx) {
            throw new NotImplementedException(format("Apply Function hasn't been implemented yet for %s", type()));
        }
    }
}
