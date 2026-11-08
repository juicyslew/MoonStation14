package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile;
import com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest;
import net.minecraft.resources.ResourceLocation;

/** Presentation selection shared by locally predicted and observer arcs. */
final class MeleeDisarmPresentationPolicy {
    record AtlasWindow(float u0, float u1, float v0, float v1) { }
    private MeleeDisarmPresentationPolicy() { }

    static ResourceLocation texture(MeleeArcProfile profile, MeleeAttackRequest.Mode mode) {
        return mode == MeleeAttackRequest.Mode.DISARM && profile.disarmTexture() != null
                ? profile.disarmTexture() : profile.texture();
    }

    static boolean dedicatedGlyph(MeleeArcProfile profile, MeleeAttackRequest.Mode mode) {
        return mode == MeleeAttackRequest.Mode.DISARM && profile.disarmTexture() != null;
    }

    enum StrikePresentation { DISARM_GLYPH, TEXTURE_GLYPH, WEAPON_ONLY, UNARMED_TRAIL }
    static StrikePresentation strikePresentation(MeleeArcProfile profile, MeleeAttackRequest.Mode mode,
                                                  boolean hasCopiedWeapon) {
        if (dedicatedGlyph(profile, mode)) return StrikePresentation.DISARM_GLYPH;
        if (hasCopiedWeapon) return StrikePresentation.WEAPON_ONLY;
        if (profile.strikeTextureGlyph()) return StrikePresentation.TEXTURE_GLYPH;
        return StrikePresentation.UNARMED_TRAIL;
    }

    static AtlasWindow atlasWindow(double progress, int frameCount) {
        return atlasWindow(progress, frameCount, frameCount);
    }

    static AtlasWindow atlasWindow(double progress, int frameCount, int columns) {
        if (frameCount < 1) throw new IllegalArgumentException("frameCount must be positive");
        if (columns < 1 || columns > frameCount) throw new IllegalArgumentException("columns must be in [1, frameCount]");
        int frame = Math.min(frameCount - 1, Math.max(0, (int) (Math.max(0, Math.min(1, progress)) * frameCount)));
        int rows = (frameCount + columns - 1) / columns;
        int column = frame % columns, row = frame / columns;
        return new AtlasWindow(column / (float) columns, (column + 1) / (float) columns,
                1f - (row + 1) / (float) rows, 1f - row / (float) rows);
    }

    static float glyphHalfExtent(double trailWidth) { return (float) trailWidth * 2f; }

    static float texturedGlyphAlpha(MeleeArcProfile profile, double sampledAlpha) {
        if (profile == null) return 0f;
        return profile.fadeOut() ? (float)Math.max(0, Math.min(1, sampledAlpha)) : 1f;
    }
}
