package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/** Request-scoped, server-resolved provider context. No client ray, body carrier or arbitrary chunk lookup. */
public record ExamineContext(Entity actor, ServerLevel level, ExamineTarget target, Entity targetEntity,
                             BlockPos rootBlock, BlockState blockState, BlockEntity blockEntity,
                             boolean detailsAllowed) { }
