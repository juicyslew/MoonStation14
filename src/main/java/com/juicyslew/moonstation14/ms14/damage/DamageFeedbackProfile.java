package com.juicyslew.moonstation14.ms14.damage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Independent presentation consequences for a semantic damage source. */
public record DamageFeedbackProfile(boolean redFlash, boolean cameraShake, boolean interruptActions, boolean hurtSound) {
    public static final Codec<DamageFeedbackProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("red_flash", false).forGetter(DamageFeedbackProfile::redFlash),
            Codec.BOOL.optionalFieldOf("camera_shake", false).forGetter(DamageFeedbackProfile::cameraShake),
            Codec.BOOL.optionalFieldOf("interrupt_actions", false).forGetter(DamageFeedbackProfile::interruptActions),
            Codec.BOOL.optionalFieldOf("hurt_sound", false).forGetter(DamageFeedbackProfile::hurtSound)
    ).apply(i, DamageFeedbackProfile::new));
    public static final DamageFeedbackProfile QUIET = new DamageFeedbackProfile(false, false, false, false);
    public static final DamageFeedbackProfile PHYSICAL = new DamageFeedbackProfile(true, true, true, false);
}

