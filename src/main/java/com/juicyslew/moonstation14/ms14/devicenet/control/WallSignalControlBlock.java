package com.juicyslew.moonstation14.ms14.devicenet.control;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceLinkSavedData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/** Wall-mounted shell for prototype-defined signal controls. */
public final class WallSignalControlBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    private static final VoxelShape NORTH=Block.box(4,4,13,12,12,16), SOUTH=Block.box(4,4,0,12,12,3), EAST=Block.box(0,4,4,3,12,12), WEST=Block.box(13,4,4,16,12,12), UP=Block.box(4,0,4,12,3,12), DOWN=Block.box(4,13,4,12,16,12);
    public WallSignalControlBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(ON,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,ON); }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) { BlockState state=defaultBlockState().setValue(FACING,context.getClickedFace()); return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null; }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { Direction facing=state.getValue(FACING); BlockPos host=pos.relative(facing.getOpposite()); return level.getBlockState(host).isFaceSturdy(level,host,facing); }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,net.minecraft.world.level.LevelAccessor level,BlockPos pos,BlockPos neighborPos) { if(direction==state.getValue(FACING).getOpposite()&&!state.canSurvive(level,pos))return Blocks.AIR.defaultBlockState(); return super.updateShape(state,direction,neighbor,level,pos,neighborPos); }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return switch(state.getValue(FACING)){case NORTH->NORTH;case SOUTH->SOUTH;case EAST->EAST;case WEST->WEST;case UP->UP;case DOWN->DOWN;}; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return ModBlockEntities.WALL_SIGNAL_CONTROL.get().create(pos,state); }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.phys.BlockHitResult hit) {
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(level.getBlockEntity(pos) instanceof WallSignalControlBlockEntity control)control.activate(player);
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean moved) {
        if(state.getBlock()!=newState.getBlock()&&level instanceof ServerLevel server
                &&level.getBlockEntity(pos) instanceof WallSignalControlBlockEntity control)
        {
            DeviceNetworkRuntime.forLevel(server).retired(control.identity());
            DeviceLinkSavedData.forMutation(server).setDirty();
        }
        super.onRemove(state,level,pos,newState,moved);
    }
}

