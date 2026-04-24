package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public sealed interface ConditionData permits ConditionData.ReagentCondition {
    String type();
    public boolean test(Entity entity);

    Codec<ConditionData> CODEC = Codec.STRING.dispatch(
            "type",
            ConditionData::type,
            type -> switch (type) {
                case "ReagentCondition" -> ConditionData.ReagentCondition.CODEC;
                default -> throw new IllegalStateException("Unknown condition type: " + type);
            }
    );

    record ReagentCondition(ResourceKey<ReagentData> reagent, float min) implements ConditionData {
        public static final MapCodec<ReagentCondition> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                LENIENT_ID_CODEC.xmap(
                        rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                        ResourceKey::location
                ).fieldOf("reagent").forGetter(ReagentCondition::reagent),
                Codec.FLOAT.fieldOf("min").forGetter(ReagentCondition::min)
        ).apply(inst, ReagentCondition::new));

        @Override public String type() { return "ReagentCondition"; }
        @Override public boolean test(Entity entity) {
            var reagentContainer = entity.getData(ModDataAttachments.REAGENT.get()).getMap();
            return reagentContainer.getOrDefault(reagent, 0f) >= min;
        }
    }
}
