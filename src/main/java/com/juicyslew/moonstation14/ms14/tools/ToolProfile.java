package com.juicyslew.moonstation14.ms14.tools;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata;

/** Resolved, supported tool-related item components. */
public record ToolProfile(Tool tool, Prying prying, String useSound, ExamineMetadata metadata,
                            Set<String> tags, Shovel shovel, BotanySwab botanySwab, BotanySampleTaker botanySampleTaker,
                             Sharp sharp, Knife knife) {
    public ToolProfile(Tool tool, Prying prying, String useSound, ExamineMetadata metadata, Set<String> tags, Shovel shovel, BotanySwab swab) { this(tool, prying, useSound, metadata, tags, shovel, swab, null, null, null); }
    public ToolProfile(Tool tool, Prying prying, String useSound) { this(tool, prying, useSound, ExamineMetadata.EMPTY, Set.of(), null, null, null, null, null); }
    public ToolProfile(Tool tool, Prying prying, String useSound, ExamineMetadata metadata) { this(tool, prying, useSound, metadata, Set.of(), null, null, null, null, null); }
    /** Compatibility constructor for resolved compositions predating the optional knife utensil capability. */
    public ToolProfile(Tool tool, Prying prying, String useSound, ExamineMetadata metadata, Set<String> tags, Shovel shovel,
                       BotanySwab swab, BotanySampleTaker sampler, Sharp sharp) {
        this(tool, prying, useSound, metadata, tags, shovel, swab, sampler, sharp, null);
    }
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

    public record Shovel(double speedModifier) {
        public static final Codec<Shovel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("speedModifier").forGetter(Shovel::speedModifier)
        ).apply(i, Shovel::new));
    }

    /** A timed botany swab action; deliberately not a tool quality. */
    public record BotanySwab(double delaySeconds) {
        public static final Codec<BotanySwab> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("delaySeconds", 2.0).forGetter(BotanySwab::delaySeconds)
        ).apply(i, BotanySwab::new));
    }
    /** Immediate source PlantSampleTaker capability; distinct from cutting quality and pollen swabbing. */
    public record BotanySampleTaker() {
        public static final Codec<BotanySampleTaker> CODEC = Codec.unit(new BotanySampleTaker());
    }
    /** Explicit SS14 SharpComponent marker; distinct from cutting quality and knife semantics. */
    public record Sharp(double butcherDelayModifier) {
        public Sharp() { this(1.0); }
        public static final Codec<Sharp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("butcherDelayModifier", 1.0).forGetter(Sharp::butcherDelayModifier)
        ).apply(i, Sharp::new));
    }
    /** Utensil capability used by SliceableFood; unrelated to Sharp and CUTTING. */
    public record Knife() { public static final Codec<Knife> CODEC = Codec.unit(new Knife()); }

    private static final Codec<Set<String>> TAGS_CODEC = Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf);

    public static final Codec<ToolProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Tool.CODEC.optionalFieldOf("tool").forGetter(p -> java.util.Optional.ofNullable(p.tool())),
            Prying.CODEC.optionalFieldOf("prying").forGetter(p -> java.util.Optional.ofNullable(p.prying())),
            Codec.STRING.optionalFieldOf("useSound").forGetter(p -> java.util.Optional.ofNullable(p.useSound())),
            ExamineMetadata.CODEC.optionalFieldOf("metadata", ExamineMetadata.EMPTY).forGetter(ToolProfile::metadata),
            TAGS_CODEC.optionalFieldOf("tags", Set.of()).forGetter(ToolProfile::tags),
            Shovel.CODEC.optionalFieldOf("shovel").forGetter(p -> java.util.Optional.ofNullable(p.shovel())),
             BotanySwab.CODEC.optionalFieldOf("botanySwab").forGetter(p -> java.util.Optional.ofNullable(p.botanySwab())),
             BotanySampleTaker.CODEC.optionalFieldOf("botanySampleTaker").forGetter(p -> java.util.Optional.ofNullable(p.botanySampleTaker()))
              , Sharp.CODEC.optionalFieldOf("sharp").forGetter(p -> java.util.Optional.ofNullable(p.sharp())),
              Knife.CODEC.optionalFieldOf("knife").forGetter(p -> java.util.Optional.ofNullable(p.knife()))
       ).apply(i, (tool, prying, useSound, metadata, tags, shovel, swab, sampler, sharp, knife) ->
               new ToolProfile(tool.orElse(null), prying.orElse(null), useSound.orElse(null), metadata, tags, shovel.orElse(null), swab.orElse(null), sampler.orElse(null), sharp.orElse(null), knife.orElse(null))));

    public void validate() {
        if (tool != null && (tool.qualities() == null || tool.qualities().isEmpty()
                || tool.qualities().stream().anyMatch(java.util.Objects::isNull)
                || tool.qualities().stream().distinct().count() != tool.qualities().size()))
            throw new IllegalArgumentException("tool qualities must be non-empty and distinct");
        if (prying != null && (!Double.isFinite(prying.speedModifier()) || prying.speedModifier() <= 0))
            throw new IllegalArgumentException("prying speed modifier must be finite and positive");
        if (tags == null || tags.stream().anyMatch(tag -> tag == null || tag.isBlank()))
            throw new IllegalArgumentException("tool tags must be non-empty strings");
        if (shovel != null && (!Double.isFinite(shovel.speedModifier()) || shovel.speedModifier() <= 0))
            throw new IllegalArgumentException("shovel speed modifier must be finite and positive");
        if (botanySwab != null && (!Double.isFinite(botanySwab.delaySeconds()) || botanySwab.delaySeconds() <= 0))
            throw new IllegalArgumentException("botany swab delay must be finite and positive");
        if (sharp != null && (!Double.isFinite(sharp.butcherDelayModifier()) || sharp.butcherDelayModifier() <= 0))
            throw new IllegalArgumentException("sharp butcher delay modifier must be finite and positive");
        if (useSound != null && !useSound.equals("moonstation14:tools/wirecutter"))
            throw new IllegalArgumentException("unsupported resolved tool use sound: " + useSound);
    }
}
