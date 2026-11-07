package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;

final class GunSoundDefaults {
    private GunSoundDefaults() { }

    static PositionalSoundCue cue(String path) {
        return new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/" + path), 0f, 0f);
    }
}
