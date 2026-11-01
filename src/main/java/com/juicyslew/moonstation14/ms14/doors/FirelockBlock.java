package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

/** Two-cell firelock host. Only its lower half owns state. */
public final class FirelockBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN=BlockStateProperties.OPEN;
    public FirelockBlock(Properties properties){super(properties.isSuffocating((s,l,p)->false));registerDefaultState(stateDefinition.any().setValue(HALF,DoubleBlockHalf.LOWER).setValue(FACING,Direction.NORTH).setValue(OPEN,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(HALF,FACING,OPEN);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext c){BlockPos p=c.getClickedPos(),up=p.above();if(p.getY()+1>=c.getLevel().getMaxBuildHeight()||!c.getLevel().hasChunkAt(up)||!c.getLevel().isInWorldBounds(up)||!c.getLevel().getWorldBorder().isWithinBounds(up)||!c.getLevel().getBlockState(up).canBeReplaced(c))return null;if(c.getPlayer()!=null&&!c.getLevel().isUnobstructed(defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite()),up,CollisionContext.of(c.getPlayer())))return null;return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public boolean canSurvive(BlockState s,LevelReader l,BlockPos p){return s.getValue(HALF)==DoubleBlockHalf.LOWER||l.getBlockState(p.below()).is(this);}
    @Override public BlockState updateShape(BlockState s,Direction d,BlockState n,net.minecraft.world.level.LevelAccessor l,BlockPos p,BlockPos np){if(s.getValue(HALF)==DoubleBlockHalf.LOWER&&d==Direction.UP&&(!n.is(this)||n.getValue(HALF)!=DoubleBlockHalf.UPPER))return Blocks.AIR.defaultBlockState();if(s.getValue(HALF)==DoubleBlockHalf.UPPER&&d==Direction.DOWN&&(!n.is(this)||n.getValue(HALF)!=DoubleBlockHalf.LOWER))return Blocks.AIR.defaultBlockState();if(!s.canSurvive(l,p))return Blocks.AIR.defaultBlockState();if(s.getValue(HALF)==DoubleBlockHalf.LOWER&&d==Direction.UP&&n.is(this))return s.setValue(OPEN,n.getValue(OPEN));return super.updateShape(s,d,n,l,p,np);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){BlockPos root=s.getValue(HALF)==DoubleBlockHalf.UPPER?p.below():p;if(l.getBlockEntity(root) instanceof FirelockBlockEntity be){if(be.isCrushExempt(c))return Shapes.empty();if(!be.projectionCollidable())return Shapes.empty();}return Shapes.block();}
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(HALF)==DoubleBlockHalf.LOWER?ModBlockEntities.FIRELOCK.get().create(p,s):null;}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(l,p,s,placer,stack);BlockPos up=p.above();l.setBlock(up,s.setValue(HALF,DoubleBlockHalf.UPPER),3);if(l.getBlockEntity(p) instanceof FirelockBlockEntity be){be.initializeFromResolvedPrototype();be.refreshProjection();}}
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState n,boolean moved){if(!s.is(n.getBlock())){BlockPos other=p.relative(s.getValue(HALF)==DoubleBlockHalf.LOWER?Direction.UP:Direction.DOWN);if(l.getBlockState(other).is(this))l.setBlock(other,Blocks.AIR.defaultBlockState(),3);}super.onRemove(s,l,p,n,moved);}
    @Override public void tick(BlockState s,ServerLevel level,BlockPos p,net.minecraft.util.RandomSource random){BlockPos root=s.getValue(HALF)==DoubleBlockHalf.UPPER?p.below():p;if(level.getBlockEntity(root) instanceof FirelockBlockEntity be)be.onScheduledTick(level);}
}
