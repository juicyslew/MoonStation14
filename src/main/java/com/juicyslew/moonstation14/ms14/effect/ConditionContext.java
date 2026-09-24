package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.util.enums.MetabolizerTypeEnum;
import com.juicyslew.moonstation14.util.enums.MobStateEnum;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable capabilities supplied to condition evaluation. An empty Optional means that the
 * capability is unavailable; a present false, empty map, or empty set is an available value.
 */
public record ConditionContext(
        Optional<Map<ResourceKey<ReagentData>, Float>> sourceReagentQuantities,
        Optional<MobStateEnum> mobState,
        Optional<Set<MetabolizerTypeEnum>> metabolizerTypes,
        Optional<Float> temperature,
        Optional<Float> hunger,
        Optional<Boolean> breathing,
        Optional<Boolean> internals,
        Optional<Set<String>> tags
) {
    public ConditionContext {
        Objects.requireNonNull(sourceReagentQuantities, "sourceReagentQuantities");
        Objects.requireNonNull(mobState, "mobState");
        Objects.requireNonNull(metabolizerTypes, "metabolizerTypes");
        Objects.requireNonNull(temperature, "temperature");
        Objects.requireNonNull(hunger, "hunger");
        Objects.requireNonNull(breathing, "breathing");
        Objects.requireNonNull(internals, "internals");
        Objects.requireNonNull(tags, "tags");

        sourceReagentQuantities = sourceReagentQuantities.map(ConditionContext::copyReagentQuantities);
        metabolizerTypes = metabolizerTypes.map(Set::copyOf);
        tags = tags.map(Set::copyOf);
    }

    public static ConditionContext unavailable() {
        return new ConditionContext(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public ConditionContext withHungerIfUnavailable(float value) {
        if (hunger.isPresent()) return this;
        return new ConditionContext(sourceReagentQuantities, mobState, metabolizerTypes, temperature,
                Optional.of(value), breathing, internals, tags);
    }

    private static Map<ResourceKey<ReagentData>, Float> copyReagentQuantities(
            Map<ResourceKey<ReagentData>, Float> quantities) {
        Objects.requireNonNull(quantities, "quantities");
        for (Map.Entry<ResourceKey<ReagentData>, Float> entry : quantities.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "reagent key");
            Objects.requireNonNull(entry.getValue(), "reagent quantity");
        }
        return Map.copyOf(quantities);
    }

    public static final class Builder {
        private Optional<Map<ResourceKey<ReagentData>, Float>> sourceReagentQuantities = Optional.empty();
        private Optional<MobStateEnum> mobState = Optional.empty();
        private Optional<Set<MetabolizerTypeEnum>> metabolizerTypes = Optional.empty();
        private Optional<Float> temperature = Optional.empty();
        private Optional<Float> hunger = Optional.empty();
        private Optional<Boolean> breathing = Optional.empty();
        private Optional<Boolean> internals = Optional.empty();
        private Optional<Set<String>> tags = Optional.empty();

        public Builder sourceReagentQuantities(Map<ResourceKey<ReagentData>, Float> value) {
            sourceReagentQuantities = Optional.of(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder mobState(MobStateEnum value) {
            mobState = Optional.of(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder metabolizerTypes(Set<MetabolizerTypeEnum> value) {
            metabolizerTypes = Optional.of(Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder temperature(float value) {
            temperature = Optional.of(value);
            return this;
        }

        public Builder hunger(float value) {
            hunger = Optional.of(value);
            return this;
        }

        public Builder breathing(boolean value) {
            breathing = Optional.of(value);
            return this;
        }

        public Builder internals(boolean value) {
            internals = Optional.of(value);
            return this;
        }

        public Builder tags(Set<String> value) {
            tags = Optional.of(Objects.requireNonNull(value, "value"));
            return this;
        }

        public ConditionContext build() {
            return new ConditionContext(sourceReagentQuantities, mobState, metabolizerTypes,
                    temperature, hunger, breathing, internals, tags);
        }
    }
}
