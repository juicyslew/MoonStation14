package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/** Medical states supported by this runtime for a character prototype. */
public record MobStateComponent(Set<State> allowedStates) implements CharacterComponent {
    public enum State { ALIVE, CRITICAL, DEAD }
    public static final String TYPE = "MobState";
    public static final Set<State> DEFAULT_ALLOWED_STATES = Set.of(State.ALIVE, State.CRITICAL, State.DEAD);
    public static final Codec<State> STATE_CODEC = Codec.STRING.xmap(value -> State.valueOf(value.toUpperCase()), State::name);
    public static final Codec<MobStateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(MobStateComponent::type),
            STATE_CODEC.listOf().optionalFieldOf("allowed_states", DEFAULT_ALLOWED_STATES.stream().toList()).forGetter(c -> c.allowedStates().stream().toList())
    ).apply(i, (type, states) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        if (states.isEmpty() || Set.copyOf(states).size() != states.size()) throw new IllegalArgumentException("allowed_states must be nonempty and unique");
        return new MobStateComponent(Set.copyOf(states));
    }));
    public MobStateComponent { allowedStates = Set.copyOf(allowedStates); if (allowedStates.isEmpty()) throw new IllegalArgumentException("allowedStates must not be empty"); }
    @Override public String type() { return TYPE; }
}
