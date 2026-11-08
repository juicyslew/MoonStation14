package com.juicyslew.moonstation14.ms14.combat;

/** Deterministic row-major RSI atlas animation selection (one Minecraft tick = 0.05s). */
public final class ProjectileSpriteFrames {
    private ProjectileSpriteFrames() {}
    public record Frame(int index, float u0, float u1, float v0, float v1) {}
    public static Frame at(ProjectileProfile.SpriteData sprite, int tick, float partialTick) {
        if (sprite == null) throw new IllegalArgumentException("sprite required");
        int index = sprite.frameSeconds() <= 0 ? 0 : Math.floorMod((int)Math.floor((tick + partialTick) * 0.05d / (double) sprite.frameSeconds() + 1.0e-6d), sprite.frameCount());
        int columns=sprite.frameColumns();
        int rows=(sprite.frameCount()+columns-1)/columns;
        int column=index%columns,row=index/columns;
        return new Frame(index,(float)column/columns,(float)(column+1)/columns,(float)row/rows,(float)(row+1)/rows);
    }
}
