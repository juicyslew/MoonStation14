package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.juicyslew.moonstation14.util.enums.MobStateEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public sealed interface ConditionData permits
        ConditionData.ReagentCondition,
        ConditionData.MobStateCondition,
        ConditionData.MetabolizerTypeCondition,
        ConditionData.TemperatureCondition,
        ConditionData.BreathingCondition,
        ConditionData.InternalsCondition,
        ConditionData.TagCondition,
        ConditionData.HungerCondition
{
    String type();
    public boolean test(Entity entity);

    Codec<ConditionData> CODEC = Codec.STRING.dispatch(
            "type",
            ConditionData::type,
            type -> switch (type) {
                case "ReagentCondition"                 -> ConditionData.ReagentCondition.CODEC;
                case "MobStateCondition"                -> ConditionData.MobStateCondition.CODEC;
                case "MetabolizerTypeCondition"         -> ConditionData.MetabolizerTypeCondition.CODEC;
                case "TemperatureCondition"             -> ConditionData.TemperatureCondition.CODEC;
                case "BreathingCondition"               -> ConditionData.BreathingCondition.CODEC;
                case "InternalsCondition"               -> ConditionData.InternalsCondition.CODEC;
                case "TagCondition"                     -> ConditionData.TagCondition.CODEC;
                case "HungerCondition"                  -> ConditionData.HungerCondition.CODEC;

                default -> throw new IllegalStateException("Unknown condition type: " + type);
            }
    );

    record ReagentCondition(ResourceKey<ReagentData> reagent, float max, float min) implements ConditionData {
        public static final MapCodec<ReagentCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ).fieldOf("reagent").forGetter(ReagentCondition::reagent),
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(ReagentCondition::max),
                Codec.FLOAT.optionalFieldOf("min", Float.MIN_VALUE).forGetter(ReagentCondition::min)
        ).apply(inst, ReagentCondition::new));

        @Override public String type() { return "ReagentCondition"; }
        @Override public boolean test(Entity entity) {
            var reagentContainer = entity.getData(ModDataAttachments.REAGENT.get()).getMap();
            return reagentContainer.getOrDefault(reagent, 0f) >= min;
        }
    }

    record MobStateCondition(MobStateEnum mobState) implements ConditionData {
        public static final MapCodec<MobStateCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                MobStateEnum.CODEC.fieldOf("mobstate").forGetter(MobStateCondition::mobState)
        ).apply(inst, MobStateCondition::new));

        @Override public String type() { return "MobStateCondition"; }
        @Override public boolean test(Entity entity) {
            if (entity instanceof LivingEntity livingEntity){
                // TODO: Make this based on custom mob data. Will have to figure that out later.
                // For now, consider 25% health to be "critical"
                float healthPercentage = livingEntity.getHealth() / livingEntity.getMaxHealth();
                MobStateEnum current_state;
                if (healthPercentage > .25f) {
                    current_state = MobStateEnum.ALIVE;
                }else if (healthPercentage > 0f){
                    current_state = MobStateEnum.CRITICAL;
                }else{
                    current_state = MobStateEnum.DEAD;
                }
                return current_state == mobState;
            }
            return false; // Idk exactly what to do here. Is it reasonable to expect that an entity that metabolizes reagents is necessarily a living one.
        }
    }

    record MetabolizerTypeCondition(List<MetabolizerTypeEnum> metabolizerType, boolean inverted) implements ConditionData {
        public static final MapCodec<MetabolizerTypeCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.list(MetabolizerTypeEnum.CODEC).fieldOf("subtype").forGetter(MetabolizerTypeCondition::metabolizerType),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(MetabolizerTypeCondition::inverted)
        ).apply(inst, MetabolizerTypeCondition::new));

        @Override public String type() { return "MetabolizerTypeCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Handle different species.
            // For now, treat everything like it's human
            if (metabolizerType.contains(MetabolizerTypeEnum.HUMAN)){
                return true;
            }
            return false;
        }
    }

    record TemperatureCondition(float max, float min) implements ConditionData {
        public static final MapCodec<TemperatureCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(TemperatureCondition::max),
                Codec.FLOAT.optionalFieldOf("min", Float.MIN_VALUE).forGetter(TemperatureCondition::min)
        ).apply(inst, TemperatureCondition::new));

        @Override public String type() { return "TemperatureCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Implement
            float entityTemperature = 293.15f; // placeholder temp (room temperature)
            if (entityTemperature > min() && entityTemperature < max()){
                return true;
            }
            return false;
        }
    }

    record BreathingCondition() implements ConditionData {
        public static final MapCodec<BreathingCondition> CODEC = MapCodec.unit(new BreathingCondition());

        @Override public String type() { return "BreathingCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Implement
            return true;
        }
    }

    record InternalsCondition(boolean inverted) implements ConditionData {
        // Whether or not you are currently using internals. (like an EVA suit's internal breathing system)
        public static final MapCodec<InternalsCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(InternalsCondition::inverted)
        ).apply(inst, InternalsCondition::new));

        @Override public String type() { return "InternalsCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Implement
            return true;
        }
    }

    record TagCondition(boolean inverted, String tag) implements ConditionData {
        // Whether or not you are currently using internals. (like an EVA suit's internal breathing system)
        public static final MapCodec<TagCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(TagCondition::inverted),
                Codec.STRING.fieldOf("tag").forGetter(TagCondition::tag)
        ).apply(inst, TagCondition::new));

        @Override public String type() { return "TagCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Implement
            return true;
        }
    }

    record HungerCondition(float max, float min) implements ConditionData {
        public static final MapCodec<HungerCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("max", Float.MAX_VALUE).forGetter(HungerCondition::max),
                Codec.FLOAT.optionalFieldOf("min", Float.MIN_VALUE).forGetter(HungerCondition::min)
        ).apply(inst, HungerCondition::new));

        @Override public String type() { return "HungerCondition"; }
        @Override public boolean test(Entity entity) {
            // TODO: Implement
            return true;
        }
    }
}
