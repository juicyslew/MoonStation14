package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.juicyslew.moonstation14.util.enums.MobStateEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Objects;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

/** Serialized, data-only condition definitions. Evaluation belongs to {@code ConditionSystem}. */
public sealed interface ConditionData permits
        ConditionData.ReagentCondition,
        ConditionData.MobStateCondition,
        ConditionData.MetabolizerTypeCondition,
        ConditionData.TemperatureCondition,
        ConditionData.BreathingCondition,
        ConditionData.InternalsCondition,
        ConditionData.TagCondition,
        ConditionData.HungerCondition {
    String type();
    boolean inverted();

    Codec<ConditionData> CODEC = Codec.STRING.dispatch(
            "type",
            ConditionData::type,
            type -> switch (type) {
                case "ReagentCondition" -> ConditionData.ReagentCondition.CODEC;
                case "MobStateCondition" -> ConditionData.MobStateCondition.CODEC;
                case "MetabolizerTypeCondition" -> ConditionData.MetabolizerTypeCondition.CODEC;
                case "TemperatureCondition" -> ConditionData.TemperatureCondition.CODEC;
                case "BreathingCondition" -> ConditionData.BreathingCondition.CODEC;
                case "InternalsCondition" -> ConditionData.InternalsCondition.CODEC;
                case "TagCondition" -> ConditionData.TagCondition.CODEC;
                case "HungerCondition" -> ConditionData.HungerCondition.CODEC;
                default -> throw new IllegalStateException("Unknown condition type: " + type);
            });

    record ReagentCondition(ResourceKey<ReagentData> reagent, float max, float min, boolean inverted)
            implements ConditionData {
        public static final MapCodec<ReagentCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ).fieldOf("reagent").forGetter(ReagentCondition::reagent),
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(ReagentCondition::max),
                Codec.FLOAT.optionalFieldOf("min", 0f).forGetter(ReagentCondition::min),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(ReagentCondition::inverted)
        ).apply(inst, ReagentCondition::new));

        public ReagentCondition {
            Objects.requireNonNull(reagent, "reagent");
        }

        public ReagentCondition(ResourceKey<ReagentData> reagent, float max, float min) {
            this(reagent, max, min, false);
        }

        @Override public String type() { return "ReagentCondition"; }
    }

    record MobStateCondition(MobStateEnum mobState, boolean inverted) implements ConditionData {
        public static final MapCodec<MobStateCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                MobStateEnum.CODEC.fieldOf("mobstate").forGetter(MobStateCondition::mobState),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(MobStateCondition::inverted)
        ).apply(inst, MobStateCondition::new));

        public MobStateCondition {
            Objects.requireNonNull(mobState, "mobState");
        }

        public MobStateCondition(MobStateEnum mobState) {
            this(mobState, false);
        }

        @Override public String type() { return "MobStateCondition"; }
    }

    record MetabolizerTypeCondition(List<MetabolizerTypeEnum> metabolizerType, boolean inverted)
            implements ConditionData {
        public static final MapCodec<MetabolizerTypeCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(MetabolizerTypeEnum.CODEC).fieldOf("subtype").forGetter(MetabolizerTypeCondition::metabolizerType),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(MetabolizerTypeCondition::inverted)
        ).apply(inst, MetabolizerTypeCondition::new));

        public MetabolizerTypeCondition {
            Objects.requireNonNull(metabolizerType, "metabolizerType");
            metabolizerType = List.copyOf(metabolizerType);
        }

        @Override public String type() { return "MetabolizerTypeCondition"; }
    }

    record TemperatureCondition(float max, float min, boolean inverted) implements ConditionData {
        public static final MapCodec<TemperatureCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(TemperatureCondition::max),
                Codec.FLOAT.optionalFieldOf("min", 0f).forGetter(TemperatureCondition::min),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(TemperatureCondition::inverted)
        ).apply(inst, TemperatureCondition::new));

        public TemperatureCondition(float max, float min) {
            this(max, min, false);
        }

        @Override public String type() { return "TemperatureCondition"; }
    }

    record BreathingCondition(boolean inverted) implements ConditionData {
        public static final MapCodec<BreathingCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(BreathingCondition::inverted)
        ).apply(inst, BreathingCondition::new));

        public BreathingCondition() {
            this(false);
        }

        @Override public String type() { return "BreathingCondition"; }
    }

    record InternalsCondition(boolean inverted) implements ConditionData {
        public static final MapCodec<InternalsCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(InternalsCondition::inverted)
        ).apply(inst, InternalsCondition::new));

        @Override public String type() { return "InternalsCondition"; }
    }

    record TagCondition(boolean inverted, String tag) implements ConditionData {
        public static final MapCodec<TagCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(TagCondition::inverted),
                Codec.STRING.fieldOf("tag").forGetter(TagCondition::tag)
        ).apply(inst, TagCondition::new));

        public TagCondition {
            Objects.requireNonNull(tag, "tag");
        }

        public TagCondition(String tag) {
            this(false, tag);
        }

        public TagCondition(String tag, boolean inverted) {
            this(inverted, tag);
        }

        @Override public String type() { return "TagCondition"; }
    }

    record HungerCondition(float max, float min, boolean inverted) implements ConditionData {
        public static final MapCodec<HungerCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(HungerCondition::max),
                Codec.FLOAT.optionalFieldOf("min", 0f).forGetter(HungerCondition::min),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(HungerCondition::inverted)
        ).apply(inst, HungerCondition::new));

        public HungerCondition(float max, float min) {
            this(max, min, false);
        }

        @Override public String type() { return "HungerCondition"; }
    }
}
