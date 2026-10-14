package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Compact bounded layout shared by the admin builder, scenarios, and fixture checks. */
public final class SmallBotanyFixtureLayout {
    public static final int WIDTH = 11, DEPTH = 6, HEIGHT = 5;
    private SmallBotanyFixtureLayout() { }
    public static BlockPos normalTray(BlockPos o, boolean second) { return o.offset(second ? 4 : 2, 1, 2); }
    public static BlockPos dryTray(BlockPos o, boolean second) { return o.offset(second ? 4 : 2, 1, 3); }
    public static BlockPos untouchedTray(BlockPos o) { return o.offset(5, 1, 3); }
    public static BlockPos grinder(BlockPos o) { return o.offset(7, 1, 2); }
    public static BlockPos extractor(BlockPos o) { return o.offset(9, 1, 3); }
    public static BlockPos apc(BlockPos o) { return o.offset(7, 1, 1); }
    public static BlockPos lamp(BlockPos o) { return o.offset(7, 3, 2); }
    public static BlockPos sample(BlockPos o) { return o.offset(6, 2, 3); }
    public static Vec3 entry(BlockPos o) { return new Vec3(o.getX() + 6.5, o.getY() + 1, o.getZ() + 3.5); }
}
