package com.juicyslew.moonstation14.ms14.lung;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Persistent selection of the exact carried internals tank; item identity is not an ownership token. */
public record InternalsState(boolean connected, Optional<String> tankToken) {
    private static final Codec<String> TOKEN = Codec.STRING.validate(value -> value.isBlank() || value.length() > 128
            ? DataResult.error(() -> "invalid internals tank token") : DataResult.success(value));
    public static final InternalsState DISCONNECTED = new InternalsState(false, Optional.empty());
    public static final Codec<InternalsState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("connected").forGetter(InternalsState::connected),
            TOKEN.optionalFieldOf("tank_token").forGetter(InternalsState::tankToken)
    ).apply(i, InternalsState::new));
    public InternalsState {
        if (tankToken == null || (connected != tankToken.isPresent())) throw new IllegalArgumentException("invalid internals selection");
    }
    public static InternalsState connected(String token) { return new InternalsState(true, Optional.of(token)); }
}
