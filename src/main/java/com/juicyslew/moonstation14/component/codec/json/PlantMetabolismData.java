package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public sealed interface PlantMetabolismData permits
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
    }

    record PlantAdjustWeeds(float probability, float amount) implements PlantMetabolismData {
        public PlantAdjustWeeds(float amount) {
            this(1f, amount);
        }

        public static final MapCodec<PlantAdjustWeeds> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustWeeds::probability),
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustWeeds::amount)
        ).apply(inst, PlantAdjustWeeds::new));

        @Override public String type() { return "PlantAdjustWeeds"; }
    }

    record PlantAdjustPests(float probability, float amount) implements PlantMetabolismData {
        public PlantAdjustPests(float amount) {
            this(1f, amount);
        }

        public static final MapCodec<PlantAdjustPests> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustPests::probability),
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustPests::amount)
        ).apply(inst, PlantAdjustPests::new));

        @Override public String type() { return "PlantAdjustPests"; }
    }

    record PlantAdjustHealth(float probability, float amount) implements PlantMetabolismData {
        public PlantAdjustHealth(float amount) { this(1f, amount); }
        public static final MapCodec<PlantAdjustHealth> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustHealth::probability),
                Codec.FLOAT.optionalFieldOf("amount", 1f).forGetter(PlantAdjustHealth::amount)
        ).apply(inst, PlantAdjustHealth::new));

        @Override public String type() { return "PlantAdjustHealth"; }
    }

    record PlantAdjustWater(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustWater> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustWater::amount)
        ).apply(inst, PlantAdjustWater::new));

        @Override public String type() { return "PlantAdjustWater"; }
    }

    record PlantAdjustToxins(float probability, float amount) implements PlantMetabolismData {
        public PlantAdjustToxins(float amount) { this(1f, amount); }
        public static final MapCodec<PlantAdjustToxins> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustToxins::probability),
                Codec.FLOAT.optionalFieldOf("amount", 1f).forGetter(PlantAdjustToxins::amount)
        ).apply(inst, PlantAdjustToxins::new));

        @Override public String type() { return "PlantAdjustToxins"; }
    }

    record PlantCryoxadone(float probability) implements PlantMetabolismData {
        public PlantCryoxadone() { this(1f); }
        public static final MapCodec<PlantCryoxadone> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantCryoxadone::probability)
        ).apply(inst, PlantCryoxadone::new));

        @Override public String type() { return "PlantCryoxadone"; }
    }


    record PlantAffectGrowth(float probability, float amount) implements PlantMetabolismData {
        public PlantAffectGrowth(float amount) { this(1f, amount); }
        public static final MapCodec<PlantAffectGrowth> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAffectGrowth::probability),
                Codec.FLOAT.optionalFieldOf("amount", 1f).forGetter(PlantAffectGrowth::amount)
        ).apply(inst, PlantAffectGrowth::new));

        @Override public String type() { return "PlantAffectGrowth"; }
    }

    record PlantDiethylamine(float probability) implements PlantMetabolismData {
        public PlantDiethylamine() { this(1f); }
        public static final MapCodec<PlantDiethylamine> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantDiethylamine::probability)
        ).apply(inst, PlantDiethylamine::new));

        @Override public String type() { return "PlantDiethylamine"; }
    }

    record PlantAdjustMutationMod(float probability, float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustMutationMod> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustMutationMod::probability),
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustMutationMod::amount)
        ).apply(inst, PlantAdjustMutationMod::new));

        @Override public String type() { return "PlantAdjustMutationMod"; }
    }

    record PlantMutateChemicals(String randomPickBotanyReagent) implements PlantMetabolismData {
        public static final MapCodec<PlantMutateChemicals> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("randompickbotanyreagent").forGetter(PlantMutateChemicals::randomPickBotanyReagent)
        ).apply(inst, PlantMutateChemicals::new));

        @Override public String type() { return "PlantMutateChemicals"; }
    }

    record PlantPhalanximine(float probability, float minScale) implements PlantMetabolismData {
        public PlantPhalanximine(float minScale) { this(1f, minScale); }
        public static final MapCodec<PlantPhalanximine> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantPhalanximine::probability),
                Codec.FLOAT.optionalFieldOf("minscale", 0f).forGetter(PlantPhalanximine::minScale)
        ).apply(inst, PlantPhalanximine::new));

        @Override public String type() { return "PlantPhalanximine"; }
    }

    record PlantRemoveKudzu() implements PlantMetabolismData {
        public static final MapCodec<PlantRemoveKudzu> CODEC = MapCodec.unit(new PlantRemoveKudzu());

        @Override public String type() { return "PlantRemoveKudzu"; }
    }

    record RobustHarvest(float probability, float potencyLimit, float potencyIncrease, float potencySeedlessThreshold) implements PlantMetabolismData {
        public RobustHarvest() { this(1f, 50f, 3f, 30f); }
        public RobustHarvest(float potencyLimit, float potencyIncrease, float potencySeedlessThreshold) { this(1f, potencyLimit, potencyIncrease, potencySeedlessThreshold); }
        public static final MapCodec<RobustHarvest> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(RobustHarvest::probability),
                Codec.FLOAT.optionalFieldOf("potencylimit", 50f).forGetter(RobustHarvest::potencyLimit),
                Codec.FLOAT.optionalFieldOf("potencyincrease", 3f).forGetter(RobustHarvest::potencyIncrease),
                Codec.FLOAT.optionalFieldOf("potencyseedlessthreshold", 30f).forGetter(RobustHarvest::potencySeedlessThreshold)
        ).apply(inst, RobustHarvest::new));

        @Override public String type() { return "RobustHarvest"; }
    }

    record PlantAdjustMutationLevel(float amount) implements PlantMetabolismData {
        public static final MapCodec<PlantAdjustMutationLevel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(PlantAdjustMutationLevel::amount)
        ).apply(inst, PlantAdjustMutationLevel::new));

        @Override public String type() { return "PlantAdjustMutationLevel"; }
    }

    record PlantRestoreSeeds(float probability) implements PlantMetabolismData {
        public static final MapCodec<PlantRestoreSeeds> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantRestoreSeeds::probability)
        ).apply(inst, PlantRestoreSeeds::new));

        @Override public String type() { return "PlantRestoreSeeds"; }
    }

    record PlantAdjustPotency(float probability, float amount) implements PlantMetabolismData {
        public PlantAdjustPotency(float amount) { this(1f, amount); }
        public static final MapCodec<PlantAdjustPotency> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("probability", 1f).forGetter(PlantAdjustPotency::probability),
                Codec.FLOAT.optionalFieldOf("amount", 1f).forGetter(PlantAdjustPotency::amount)
        ).apply(inst, PlantAdjustPotency::new));

        @Override public String type() { return "PlantAdjustPotency"; }
    }
}

