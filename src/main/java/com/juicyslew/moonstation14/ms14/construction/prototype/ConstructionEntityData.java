package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Locally supported runtime policy for a resolved graph entity; not a generic SS14 component mirror. */
public record ConstructionEntityData(String block, boolean defaultAnchored, boolean anchorable,
                                     boolean unanchorable, double anchoringSeconds, boolean airtight,
                                     java.util.Optional<RadiationBlockerProfile> radiationBlocker) {
    public static final Codec<ConstructionEntityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("block").forGetter(ConstructionEntityData::block),
            Codec.BOOL.optionalFieldOf("defaultAnchored", true).forGetter(ConstructionEntityData::defaultAnchored),
            Codec.BOOL.optionalFieldOf("anchorable", false).forGetter(ConstructionEntityData::anchorable),
            Codec.BOOL.optionalFieldOf("unanchorable", false).forGetter(ConstructionEntityData::unanchorable),
            Codec.DOUBLE.optionalFieldOf("anchoringSeconds", 1.0).forGetter(ConstructionEntityData::anchoringSeconds),
            Codec.BOOL.optionalFieldOf("airtight", true).forGetter(ConstructionEntityData::airtight),
            Codec.DOUBLE.optionalFieldOf("radiationBlockerResistance").xmap(
                    value -> value.map(RadiationBlockerProfile::new),
                    value -> value.map(RadiationBlockerProfile::resistance))
                    .forGetter(ConstructionEntityData::radiationBlocker)
    ).apply(i, ConstructionEntityData::new));
    public ConstructionEntityData {
        if (block == null || block.isBlank() || unanchorable && !anchorable
                || !Double.isFinite(anchoringSeconds) || anchoringSeconds < 0 || radiationBlocker == null)
            throw new IllegalArgumentException("invalid construction entity mapping");
    }
    public ConstructionEntityData(String block, boolean defaultAnchored, boolean anchorable, boolean unanchorable,
                                  double anchoringSeconds, boolean airtight) {
        this(block, defaultAnchored, anchorable, unanchorable, anchoringSeconds, airtight, java.util.Optional.empty());
    }
}

