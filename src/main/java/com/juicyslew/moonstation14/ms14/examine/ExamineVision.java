package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Shared client/server vision classification; transparency is explicit and never inherits physical interaction. */
public final class ExamineVision {
    public static final TagKey<Block> TRANSPARENT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "examine_transparent"));
    private ExamineVision() { }
    public static boolean isTransparent(BlockState state) { return state.is(TRANSPARENT); }
}

