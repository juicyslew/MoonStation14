package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeDisarmPresentationPolicyTest {
    private static final ResourceLocation ORDINARY = ResourceLocation.withDefaultNamespace("arc");
    private static final ResourceLocation DISARM = ResourceLocation.withDefaultNamespace("disarm");
    private static MeleeArcProfile profile(ResourceLocation disarm) {
        return new MeleeArcProfile(ORDINARY, MeleeArcProfile.Animation.SLASH, true, 1,
                new MeleeStrikeProfile(.4, .5, .8, .1, 1), disarm);
    }
    @Test void modeSelectsDedicatedDisarmTextureWithoutChangingLightOrWide() {
        var profile = profile(DISARM);
        assertEquals(DISARM, MeleeDisarmPresentationPolicy.texture(profile, MeleeAttackRequest.Mode.DISARM));
        assertTrue(MeleeDisarmPresentationPolicy.dedicatedGlyph(profile, MeleeAttackRequest.Mode.DISARM));
        for (var mode : new MeleeAttackRequest.Mode[]{MeleeAttackRequest.Mode.LIGHT, MeleeAttackRequest.Mode.WIDE}) {
            assertEquals(ORDINARY, MeleeDisarmPresentationPolicy.texture(profile, mode));
            assertFalse(MeleeDisarmPresentationPolicy.dedicatedGlyph(profile, mode));
        }
    }
    @Test void absentOptionalTextureFallsBackToOrdinaryPresentation() {
        var profile = profile(null);
        assertEquals(ORDINARY, MeleeDisarmPresentationPolicy.texture(profile, MeleeAttackRequest.Mode.DISARM));
        assertFalse(MeleeDisarmPresentationPolicy.dedicatedGlyph(profile, MeleeAttackRequest.Mode.DISARM));
    }
    @Test void strikePresentationPrioritizesDisarmThenWeaponThenUnarmedTrail() {
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.DISARM_GLYPH,
                MeleeDisarmPresentationPolicy.strikePresentation(profile(DISARM), MeleeAttackRequest.Mode.DISARM, true));
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.WEAPON_ONLY,
                MeleeDisarmPresentationPolicy.strikePresentation(profile(null), MeleeAttackRequest.Mode.LIGHT, true));
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.UNARMED_TRAIL,
                MeleeDisarmPresentationPolicy.strikePresentation(profile(null), MeleeAttackRequest.Mode.LIGHT, false));
    }
    @Test void authoredTextureGlyphUsesProfileTextureWithoutChangingFistOrWeapons() {
        var textured = new MeleeArcProfile(ORDINARY, MeleeArcProfile.Animation.NONE, true, 1,
                new MeleeStrikeProfile(.4, .5, .8, .1, 1), null, MeleeArcProfile.WeaponPose.DEFAULT, 3, true);
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.TEXTURE_GLYPH,
                MeleeDisarmPresentationPolicy.strikePresentation(textured, MeleeAttackRequest.Mode.LIGHT, false));
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.WEAPON_ONLY,
                MeleeDisarmPresentationPolicy.strikePresentation(textured, MeleeAttackRequest.Mode.LIGHT, true));
        assertEquals(MeleeDisarmPresentationPolicy.StrikePresentation.UNARMED_TRAIL,
                MeleeDisarmPresentationPolicy.strikePresentation(profile(null), MeleeAttackRequest.Mode.LIGHT, false));
    }
    @Test void textureGlyphFadeFlagControlsAlphaWhileDurationControlsItsLifetime() {
        var fading = profile(null);
        var held = new MeleeArcProfile(ORDINARY, MeleeArcProfile.Animation.NONE, false, .399,
                new MeleeStrikeProfile(.399, .5, .8, .1, 1), null, MeleeArcProfile.WeaponPose.DEFAULT, 3, true);
        assertEquals(.25f, MeleeDisarmPresentationPolicy.texturedGlyphAlpha(fading, .25), 1e-6);
        assertEquals(1f, MeleeDisarmPresentationPolicy.texturedGlyphAlpha(held, .25));
        assertEquals(.399, held.lifetimeSeconds());
        assertEquals(.399, held.strike().durationSeconds());
    }
    @Test void textureAtlasSelectsOneOfFourClawFramesByTimelineProgress() {
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(0, .5f, .5f, 1),
                MeleeDisarmPresentationPolicy.atlasWindow(0, 4, 2));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(.5f, 1, .5f, 1),
                MeleeDisarmPresentationPolicy.atlasWindow(.25, 4, 2));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(0, .5f, 0, .5f),
                MeleeDisarmPresentationPolicy.atlasWindow(.5, 4, 2));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(.5f, 1, 0, .5f),
                MeleeDisarmPresentationPolicy.atlasWindow(.75, 4, 2));
        assertEquals(new MeleeDisarmPresentationPolicy.AtlasWindow(.5f, 1, 0, 1),
                MeleeDisarmPresentationPolicy.atlasWindow(.75, 2));
    }
}
