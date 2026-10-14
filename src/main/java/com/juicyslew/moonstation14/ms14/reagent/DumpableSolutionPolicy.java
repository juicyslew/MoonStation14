package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Locally supported projection of SS14 ChemMasterDumpableSolution; buffer is intentionally unbounded. */
public record DumpableSolutionPolicy(boolean enabled, String solutionName, boolean unlimited) {
    public static final Codec<DumpableSolutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(DumpableSolutionPolicy::enabled),
            Codec.STRING.optionalFieldOf("solutionName", "default").forGetter(DumpableSolutionPolicy::solutionName),
            Codec.BOOL.optionalFieldOf("unlimited", false).forGetter(DumpableSolutionPolicy::unlimited)
    ).apply(i, DumpableSolutionPolicy::new));
    public static final DumpableSolutionPolicy DISABLED = new DumpableSolutionPolicy(false, "default", false);
    public DumpableSolutionPolicy { if (solutionName == null || solutionName.isBlank()) throw new IllegalArgumentException("invalid dump policy"); }
}

