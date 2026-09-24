package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.ConditionData;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;

import java.util.List;
import java.util.Set;

/** Pure condition evaluation; it has no world access and emits no per-tick logging. */
public final class ConditionSystem {
    private ConditionSystem() {
    }

    public static boolean allPass(List<ConditionData> conditions, ConditionContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (ConditionData condition : conditions) {
            if (condition == null || !evaluate(condition, context)) {
                return false;
            }
        }
        return true;
    }

    public static boolean evaluate(ConditionData condition, ConditionContext context) {
        if (condition == null) {
            return false;
        }
        ConditionContext capabilities = context == null ? ConditionContext.unavailable() : context;
        boolean rawResult = switch (condition) {
            case ConditionData.ReagentCondition value -> capabilities.sourceReagentQuantities()
                    .map(solution -> solution.getOrDefault(value.reagent(), 0f) >= value.min()
                            && solution.getOrDefault(value.reagent(), 0f) <= value.max())
                    .orElse(false);
            case ConditionData.MobStateCondition value -> capabilities.mobState()
                    .map(value.mobState()::equals).orElse(false);
            case ConditionData.MetabolizerTypeCondition value -> capabilities.metabolizerTypes()
                    .map(types -> overlaps(types, value.metabolizerType())).orElse(false);
            case ConditionData.TemperatureCondition value -> capabilities.temperature()
                    .map(current -> current >= value.min() && current <= value.max()).orElse(false);
            case ConditionData.BreathingCondition ignored -> capabilities.breathing().orElse(false);
            case ConditionData.InternalsCondition ignored -> capabilities.internals().orElse(false);
            case ConditionData.TagCondition value -> capabilities.tags()
                    .map(tags -> tags.contains(value.tag())).orElse(false);
            case ConditionData.HungerCondition value -> capabilities.hunger()
                    .map(current -> current >= value.min() && current <= value.max()).orElse(false);
        };
        return rawResult ^ condition.inverted();
    }

    private static boolean overlaps(Set<MetabolizerTypeEnum> available,
                                    List<MetabolizerTypeEnum> requested) {
        for (MetabolizerTypeEnum type : requested) {
            if (available.contains(type)) {
                return true;
            }
        }
        return false;
    }
}
