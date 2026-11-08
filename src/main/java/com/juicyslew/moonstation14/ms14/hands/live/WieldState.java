package com.juicyslew.moonstation14.ms14.hands.live;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/** Exact proof for a virtual wield support-hand reservation; contains no second weapon stack. */
public record WieldState(String sourceHand, String sourceToken, String supportHand, UUID proof) {
    private static final Codec<String> TEXT = Codec.STRING.validate(s -> s.isBlank()
            ? DataResult.error(() -> "blank wield identity") : DataResult.success(s));
    public static final Codec<WieldState> CODEC = RecordCodecBuilder.create(i -> i.group(
            TEXT.fieldOf("source_hand").forGetter(WieldState::sourceHand),
            TEXT.fieldOf("source_token").forGetter(WieldState::sourceToken),
            TEXT.fieldOf("support_hand").forGetter(WieldState::supportHand),
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("proof").forGetter(WieldState::proof)
    ).apply(i, WieldState::new));
    public WieldState {
        if (sourceHand == null || sourceToken == null || supportHand == null || proof == null
                || sourceHand.isBlank() || sourceToken.isBlank() || supportHand.isBlank()
                || sourceHand.equals(supportHand)) throw new IllegalArgumentException("invalid wield ownership proof");
    }
}
