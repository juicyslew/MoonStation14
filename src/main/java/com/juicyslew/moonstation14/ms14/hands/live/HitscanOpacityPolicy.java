package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Material-family policy for opaque hitscan, independent of weapon identity. */
final class HitscanOpacityPolicy {
    private HitscanOpacityPolicy() { }
    static boolean keepsBlock(BlockState state) {
        return state.getBlock() instanceof AirlockBlock || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof TintedGlassBlock
                || state.is(Blocks.TINTED_GLASS) || state.canOcclude();
    }
}
