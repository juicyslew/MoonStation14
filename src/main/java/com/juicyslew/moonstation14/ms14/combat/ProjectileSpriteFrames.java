package com.juicyslew.moonstation14.ms14.combat;

/** Deterministic RSI horizontal-strip animation selection (one Minecraft tick = 0.05s). */
public final class ProjectileSpriteFrames {
    private ProjectileSpriteFrames() {}
    public record Frame(int index, float u0, float u1) {}
    public static Frame at(ProjectileProfile.SpriteData sprite, int tick, float partialTick) {
        if (sprite == null) throw new IllegalArgumentException("sprite required");
        int index = sprite.frameSeconds() <= 0 ? 0 : Math.floorMod((int)Math.floor((tick + partialTick) * 0.05d / (double) sprite.frameSeconds() + 1.0e-6d), sprite.frameCount());
        return new Frame(index, (float) index / sprite.frameCount(), (float) (index + 1) / sprite.frameCount());
    }
}
