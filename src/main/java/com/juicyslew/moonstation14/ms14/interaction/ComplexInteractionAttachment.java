package com.juicyslew.moonstation14.ms14.interaction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Entity capability, not an ItemStack data component. Only initialized disabled instances are tombstones. */
public record ComplexInteractionAttachment(boolean initialized, boolean enabled) {
    public static final Codec<ComplexInteractionAttachment> CODEC = RecordCodecBuilder.<ComplexInteractionAttachment>create(instance ->
            instance.group(Codec.BOOL.fieldOf("initialized").forGetter(ComplexInteractionAttachment::initialized),
                    Codec.BOOL.fieldOf("enabled").forGetter(ComplexInteractionAttachment::enabled))
                    .apply(instance, ComplexInteractionAttachment::new)).flatXmap(
            state -> state.initialized() ? DataResult.success(state)
                    : DataResult.error(() -> "complex interaction attachment must be initialized"),
            state -> state.initialized() ? DataResult.success(state)
                    : DataResult.error(() -> "complex interaction attachment must be initialized"));
}
