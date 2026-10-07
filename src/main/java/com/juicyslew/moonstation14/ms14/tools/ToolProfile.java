package com.juicyslew.moonstation14.ms14.tools;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Resolved, supported tool-related item components. */
public record ToolProfile(Tool tool, Prying prying, String useSound) {
    public enum Quality { SCREWING, CUTTING, PULSING, PRYING }

    public record Tool(List<Quality> qualities) {
        public static final Codec<Tool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Quality::valueOf, Quality::name).listOf()
                        .fieldOf("qualities").forGetter(Tool::qualities)
        ).apply(i, Tool::new));
    }

    public record Prying(double speedModifier) {
        public static final Codec<Prying> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("speedModifier").forGetter(Prying::speedModifier)
        ).apply(i, Prying::new));
    }

    public static final Codec<ToolProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Tool.CODEC.optionalFieldOf("tool").forGetter(p -> java.util.Optional.ofNullable(p.tool())),
            Prying.CODEC.optionalFieldOf("prying").forGetter(p -> java.util.Optional.ofNullable(p.prying())),
            Codec.STRING.optionalFieldOf("useSound").forGetter(p -> java.util.Optional.ofNullable(p.useSound()))
    ).apply(i, (tool, prying, useSound) -> new ToolProfile(tool.orElse(null), prying.orElse(null), useSound.orElse(null))));

    public void validate() {
        if (tool != null && (tool.qualities() == null || tool.qualities().isEmpty()
                || tool.qualities().stream().anyMatch(java.util.Objects::isNull)
                || tool.qualities().stream().distinct().count() != tool.qualities().size()))
            throw new IllegalArgumentException("tool qualities must be non-empty and distinct");
        if (prying != null && (!Double.isFinite(prying.speedModifier()) || prying.speedModifier() <= 0))
            throw new IllegalArgumentException("prying speed modifier must be finite and positive");
        if (useSound != null && !useSound.equals("moonstation14:tools/wirecutter"))
            throw new IllegalArgumentException("unsupported resolved tool use sound: " + useSound);
    }
}
