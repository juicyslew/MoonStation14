package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereBlockPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class GasMinerBlock extends Block implements EntityBlock, AtmosphereBlockPolicy {
    private final String profileIdentity;
    public GasMinerBlock(String profileIdentity, Properties properties) {
        super(properties);
        if (profileIdentity == null || profileIdentity.isBlank()) throw new IllegalArgumentException("profile identity required");
        this.profileIdentity = profileIdentity;
    }
    public String profileIdentity() { return profileIdentity; }
    // SS14 GasMiner composes Physics but not Airtight; Minecraft collision remains solid while
    // the adaptation permits the gas cell at the anchored machine position to hold atmosphere.
    @Override public boolean atmospherePassable() { return true; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return ModBlockEntities.GAS_MINER.get().create(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.GAS_MINER.get()) return null;
        return (world, pos, blockState, entity) -> { if (world instanceof ServerLevel server && entity instanceof GasMinerBlockEntity miner) GasMinerBlockEntity.tick(server, miner); };
    }
}
