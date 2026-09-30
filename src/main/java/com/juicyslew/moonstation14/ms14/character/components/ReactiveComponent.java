package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Supported target-side reagent reaction groups and methods. */
public record ReactiveComponent(List<ReactiveGroup> reactiveGroups,
                                List<ReactiveMethod> reactiveMethods) implements CharacterComponent {
    public static final String TYPE = "Reactive";
    public static final Codec<ReactiveComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ReactiveComponent::type),
            Codec.list(ReactiveGroup.CODEC).fieldOf("reactive_groups").forGetter(ReactiveComponent::reactiveGroups),
            Codec.list(ReactiveMethod.CODEC).fieldOf("reactive_methods").forGetter(ReactiveComponent::reactiveMethods)
    ).apply(instance, (type, groups, methods) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new ReactiveComponent(groups, methods);
    }));

    public ReactiveComponent {
        reactiveGroups = distinctCopy(reactiveGroups, "reactive_groups");
        reactiveMethods = distinctCopy(reactiveMethods, "reactive_methods");
        if (reactiveGroups.isEmpty() != reactiveMethods.isEmpty())
            throw new IllegalArgumentException("reactive groups and methods must both be empty or populated");
    }

    private static <T> List<T> distinctCopy(List<T> values, String field) {
        List<T> copy = List.copyOf(Objects.requireNonNull(values, field));
        if (new HashSet<>(copy).size() != copy.size())
            throw new IllegalArgumentException(field + " must not contain duplicates");
        return copy;
    }

    @Override public String type() { return TYPE; }

    public enum ReactiveGroup {
        FLAMMABLE("flammable"), EXTINGUISH("extinguish"), ACIDIC("acidic");
        public static final Codec<ReactiveGroup> CODEC = Codec.STRING.comapFlatMap(value -> {
            for (ReactiveGroup group : values()) if (group.serialized.equals(value)) return DataResult.success(group);
            return DataResult.error(() -> "unknown canonical reactive group '" + value + "'");
        }, ReactiveGroup::serialized);
        private final String serialized;
        ReactiveGroup(String serialized) { this.serialized = serialized; }
        public String serialized() { return serialized; }
    }

    public enum ReactiveMethod {
        TOUCH;
        public static final Codec<ReactiveMethod> CODEC = Codec.STRING.comapFlatMap(value ->
                "touch".equals(value) ? DataResult.success(TOUCH)
                        : DataResult.error(() -> "unknown canonical reactive method '" + value + "'"),
                ignored -> "touch");
    }
}
