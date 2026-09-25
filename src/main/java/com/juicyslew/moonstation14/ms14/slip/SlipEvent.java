package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Admitted slip attempt, dispatched before any slip motion or status changes. */
public record SlipEvent(ServerLevel level, BlockPos sourcePosition, PuddleBlockEntity source,
                        LivingEntity target, SlipperySolution.Outcome solution,
                        boolean wasSliding) {
}
