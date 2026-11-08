package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Allowed authored mouse coat variants. Runtime selection is persistent entity state. */
public record MouseAppearanceComponent(List<Variant> variants) implements CharacterComponent {
    public static final String TYPE = "MouseAppearance";
    public static final Variant DEFAULT_VARIANT = Variant.WHITE;
    public static final List<Variant> DEFAULT_VARIANTS = List.of(DEFAULT_VARIANT);
    public static final Codec<Variant> VARIANT_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(Variant.valueOf(value.toUpperCase(java.util.Locale.ROOT))); }
        catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(() -> "unknown mouse appearance variant: " + value); }
    }, variant -> variant.name().toLowerCase(java.util.Locale.ROOT));
    public static final Codec<MouseAppearanceComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").xmap(type -> { if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type); return type; }, ignored -> TYPE).forGetter(MouseAppearanceComponent::type),
            VARIANT_CODEC.listOf().optionalFieldOf("variants", DEFAULT_VARIANTS).forGetter(MouseAppearanceComponent::variants)
    ).apply(instance, (type, variants) -> new MouseAppearanceComponent(variants)));

    public MouseAppearanceComponent {
        variants = List.copyOf(variants);
        if (variants.isEmpty() || variants.stream().distinct().count() != variants.size())
            throw new IllegalArgumentException("mouse appearance variants must be nonempty and distinct");
    }
    @Override public String type() { return TYPE; }
    /** Selects a bounded entry from the resolved allowed list; index is supplied by authoritative RNG. */
    public static Variant select(List<Variant> allowed, int randomIndex) {
        if (allowed == null || allowed.isEmpty()) throw new IllegalArgumentException("mouse appearance variants must be nonempty");
        if (randomIndex < 0 || randomIndex >= allowed.size()) throw new IllegalArgumentException("random index outside allowed variants");
        return allowed.get(randomIndex);
    }
    public enum Variant { WHITE, BROWN, GRAY }
}
