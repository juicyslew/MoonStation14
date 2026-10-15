package com.juicyslew.moonstation14.block.custom;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

/** Static Minecraft adaptation of SS14's anchored singularity-startup machine. */
public final class SingularityGeneratorBlock extends Block implements EntityBlock {
    public SingularityGeneratorBlock(Properties properties) { super(properties); }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.SINGULARITY_GENERATOR.get().create(pos,state);
    }
}
