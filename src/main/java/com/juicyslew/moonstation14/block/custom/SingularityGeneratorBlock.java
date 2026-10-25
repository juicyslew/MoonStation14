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
public final class SingularityGeneratorBlock extends Block implements EntityBlock, com.juicyslew.moonstation14.ms14.singularity.SingularityConsumable {
    private final net.minecraft.resources.ResourceLocation profileId;
    public SingularityGeneratorBlock(Properties properties) { this(properties, com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.DEFAULT_ID); }
    public SingularityGeneratorBlock(Properties properties, net.minecraft.resources.ResourceLocation profileId) { super(properties); this.profileId=profileId; }
    public net.minecraft.resources.ResourceLocation profileId() { return profileId; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        var entity = ModBlockEntities.SINGULARITY_GENERATOR.get().create(pos,state);
        if (entity instanceof SingularityGeneratorBlockEntity generator) generator.setProfileId(profileId);
        return entity;
    }
}
