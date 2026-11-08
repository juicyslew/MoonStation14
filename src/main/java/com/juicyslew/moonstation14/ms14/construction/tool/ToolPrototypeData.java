package com.juicyslew.moonstation14.ms14.construction.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;
import java.util.Optional;

/** Locally supported tool capabilities and Minecraft-adapted welder fuel composition. */
public record ToolPrototypeData(Set<Quality> qualities, double speedModifier, Optional<Welder> welder, Optional<UseSound> useSound) {
    public enum Quality { Anchoring, Screwing, Welding, Prying }
    public enum UseSound { Wrench, Screwdriver, Crowbar, Welder }
    public record Welder(double initialFuel, double ignitionCost, double fuelPerSecond) {
        public Welder {
            if (!Double.isFinite(initialFuel) || initialFuel < 0 || !Double.isFinite(ignitionCost)
                    || ignitionCost < 0 || ignitionCost > initialFuel || !Double.isFinite(fuelPerSecond)
                    || fuelPerSecond < 0) throw new IllegalArgumentException("invalid welder fuel composition");
        }
    }

    private static final Codec<Welder> WELDER_CODEC = RecordCodecBuilder.create(
            (RecordCodecBuilder.Instance<Welder> w) -> w.group(
                    Codec.DOUBLE.fieldOf("initialFuel").forGetter(Welder::initialFuel),
                    Codec.DOUBLE.fieldOf("ignitionCost").forGetter(Welder::ignitionCost),
                    Codec.DOUBLE.fieldOf("fuelPerSecond").forGetter(Welder::fuelPerSecond)
            ).apply(w, Welder::new));

    public static final Codec<ToolPrototypeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().xmap(values -> values.stream().map(Quality::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    values -> values.stream().map(Enum::name).sorted().toList()).fieldOf("qualities").forGetter(ToolPrototypeData::qualities),
            Codec.DOUBLE.fieldOf("speedModifier").forGetter(ToolPrototypeData::speedModifier),
            WELDER_CODEC.optionalFieldOf("welder").forGetter(ToolPrototypeData::welder),
            Codec.STRING.xmap(UseSound::valueOf, Enum::name).optionalFieldOf("useSound").forGetter(ToolPrototypeData::useSound)
    ).apply(i, ToolPrototypeData::new));

    public ToolPrototypeData {
        qualities = Set.copyOf(qualities);
        welder = welder == null ? Optional.empty() : welder;
        useSound = useSound == null ? Optional.empty() : useSound;
        if (!Double.isFinite(speedModifier) || speedModifier <= 0)
            throw new IllegalArgumentException("tool speed modifier must be finite and positive");
    }

    public ToolPrototypeData(Set<Quality> qualities, double speedModifier, Optional<Welder> welder) {
        this(qualities, speedModifier, welder, Optional.empty());
    }
}

