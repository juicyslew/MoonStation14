package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Resolved, reusable behavior selection for an autonomous grounded mob. */
public record NpcBehaviorComponent(List<Task> tasks, float visionRange, float mealRange,
                                   float idleRange, int planIntervalTicks,
                                   int idleMinimumTicks, int idleMaximumTicks) implements CharacterComponent {
    public static final String TYPE = "NpcBehavior";
    public static final NpcBehaviorComponent DEFAULT = new NpcBehaviorComponent(
            List.of(Task.IDLE), 10f, 1f, 7f, 9, 40, 140);
    public static final Codec<Task> TASK_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(Task.valueOf(value)); }
        catch (IllegalArgumentException exception) {
            return com.mojang.serialization.DataResult.error(() -> "unknown NPC task: " + value);
        }
    }, Enum::name);
    public static final Codec<NpcBehaviorComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(NpcBehaviorComponent::type),
            Codec.list(TASK_CODEC).optionalFieldOf("tasks", DEFAULT.tasks).forGetter(NpcBehaviorComponent::tasks),
            Codec.FLOAT.optionalFieldOf("visionRange", DEFAULT.visionRange).forGetter(NpcBehaviorComponent::visionRange),
            Codec.FLOAT.optionalFieldOf("mealRange", DEFAULT.mealRange).forGetter(NpcBehaviorComponent::mealRange),
            Codec.FLOAT.optionalFieldOf("idleRange", DEFAULT.idleRange).forGetter(NpcBehaviorComponent::idleRange),
            Codec.INT.optionalFieldOf("planIntervalTicks", DEFAULT.planIntervalTicks).forGetter(NpcBehaviorComponent::planIntervalTicks),
            Codec.INT.optionalFieldOf("idleMinimumTicks", DEFAULT.idleMinimumTicks).forGetter(NpcBehaviorComponent::idleMinimumTicks),
            Codec.INT.optionalFieldOf("idleMaximumTicks", DEFAULT.idleMaximumTicks).forGetter(NpcBehaviorComponent::idleMaximumTicks)
    ).apply(instance, (type, tasks, vision, meal, idle, interval, minimum, maximum) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
        return new NpcBehaviorComponent(tasks, vision, meal, idle, interval, minimum, maximum);
    }));

    public NpcBehaviorComponent {
        tasks = List.copyOf(tasks);
        if (tasks.isEmpty() || tasks.stream().distinct().count() != tasks.size()
                || !Float.isFinite(visionRange) || !Float.isFinite(mealRange) || !Float.isFinite(idleRange)
                || visionRange <= 0 || mealRange <= 0 || idleRange <= 0 || mealRange > visionRange
                || planIntervalTicks < 1 || idleMinimumTicks < 0 || idleMaximumTicks < idleMinimumTicks)
            throw new IllegalArgumentException("invalid NPC behavior policy");
    }

    @Override public String type() { return TYPE; }

    /** Ordered branches mirror recursive SS14 compound precedence; IDLE is the fallback. */
    public enum Task { FOOD, DRINK, CLEAN, IDLE, RETALIATE }
}
