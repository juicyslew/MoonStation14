package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Resolved adaptation of SS14 AbsorbentComponent, SolutionRegenerationComponent and SolutionPurgeComponent.
 * Upstream: Content.Shared/Fluids/AbsorbentComponent.cs (pickup amount),
 * Content.Shared/Chemistry/Components/{SolutionRegenerationComponent,SolutionPurgeComponent}.cs and their
 * shared entity systems. Deliberate departure: render progress/effects/sounds and container refill are
 * not modeled; this NPC has a reagent attachment rather than an SS14 SolutionContainer.
 */
public record AbsorbentCleaningComponent(long pickupCents, long capacityCents, int cooldownTicks,
                                         long regenerationCents, long purgeCents, int cycleTicks)
        implements CharacterComponent {
    public static final String TYPE = "AbsorbentCleaning";
    public static final Codec<AbsorbentCleaningComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("type").forGetter(AbsorbentCleaningComponent::type),
            Codec.LONG.fieldOf("pickupCents").forGetter(AbsorbentCleaningComponent::pickupCents),
            Codec.LONG.fieldOf("capacityCents").forGetter(AbsorbentCleaningComponent::capacityCents),
            Codec.INT.fieldOf("cooldownTicks").forGetter(AbsorbentCleaningComponent::cooldownTicks),
            Codec.LONG.fieldOf("regenerationCents").forGetter(AbsorbentCleaningComponent::regenerationCents),
            Codec.LONG.fieldOf("purgeCents").forGetter(AbsorbentCleaningComponent::purgeCents),
            Codec.INT.fieldOf("cycleTicks").forGetter(AbsorbentCleaningComponent::cycleTicks)
    ).apply(i, (type,p,c,cd,r,pg,cycle) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown cleaning profile");
        return new AbsorbentCleaningComponent(p,c,cd,r,pg,cycle);
    }));
    public AbsorbentCleaningComponent {
        if (pickupCents <= 0 || capacityCents < pickupCents || cooldownTicks < 1 || regenerationCents < 0
                || purgeCents < 0 || cycleTicks < 1) throw new IllegalArgumentException("invalid absorbent profile");
    }
    @Override public String type() { return TYPE; }
}
