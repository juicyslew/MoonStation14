package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Immutable supported sound cues for a melee weapon. */
public record MeleeSoundData(PositionalSoundCue swing, PositionalSoundCue hit, PositionalSoundCue noDamage) {
    public static final MeleeSoundData DEFAULT = new MeleeSoundData(
            new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/punch_miss"), -3f, .025f),
            null,
            new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/effects/weak_hit"), -3f, .025f));
    public static final Codec<PositionalSoundCue> SOUND_CUE_CODEC = PositionalSoundCue.codec(-3f, .025f);
    public static final Codec<MeleeSoundData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SOUND_CUE_CODEC.optionalFieldOf("swing").forGetter(v -> java.util.Optional.ofNullable(v.swing)),
            SOUND_CUE_CODEC.optionalFieldOf("hit").forGetter(v -> java.util.Optional.ofNullable(v.hit)),
            SOUND_CUE_CODEC.optionalFieldOf("no_damage").forGetter(v -> java.util.Optional.ofNullable(v.noDamage))
    ).apply(i, (s,h,n) -> new MeleeSoundData(s.orElse(null), h.orElse(null), n.orElse(null))));
}
