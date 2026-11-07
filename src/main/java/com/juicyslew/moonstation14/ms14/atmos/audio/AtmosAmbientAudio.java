package com.juicyslew.moonstation14.ms14.atmos.audio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Shared typed SS14 AmbientSound descriptor used by atmos-machine and unary ventilation prototypes. */
public record AtmosAmbientAudio(String sound, float volumeDb, float range, Gate gate) {
    public enum Gate { TRANSFER, OPEN, TEG_DISPLAY, ENABLED_POWERED }

    private static final Codec<Gate> GATE_CODEC = Codec.STRING.comapFlatMap(value -> {
        for (Gate gate : Gate.values())
            if (gate.name().equalsIgnoreCase(value.replace('-', '_'))) return DataResult.success(gate);
        return DataResult.error(() -> "unknown Gate: " + value);
    }, gate -> gate.name().toLowerCase(java.util.Locale.ROOT));

    public static final Codec<AtmosAmbientAudio> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("sound").forGetter(AtmosAmbientAudio::sound),
            Codec.FLOAT.optionalFieldOf("volumeDb", -9f).forGetter(AtmosAmbientAudio::volumeDb),
            Codec.FLOAT.optionalFieldOf("range", 5f).forGetter(AtmosAmbientAudio::range),
            GATE_CODEC.fieldOf("gate").forGetter(AtmosAmbientAudio::gate)
    ).apply(i, AtmosAmbientAudio::new));
}
