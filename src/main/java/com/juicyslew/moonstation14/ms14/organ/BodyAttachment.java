package com.juicyslew.moonstation14.ms14.organ;

import com.mojang.serialization.Codec;
import java.util.Objects;

/** Default factory is never read as a persisted body; check hasData first. */
public record BodyAttachment(BodyState state) {
    public static final Codec<BodyAttachment> CODEC = BodyState.CODEC.xmap(BodyAttachment::new, BodyAttachment::state);
    public BodyAttachment { Objects.requireNonNull(state, "state"); }
}
