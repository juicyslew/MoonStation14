package com.juicyslew.moonstation14.ms14.disposal.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.function.BiFunction;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/** Physical owner block for a disposal tube cell. Persistent topology remains in dimension data. */
public final class DisposalTubeBlock extends Block {
    private static volatile BiFunction<Level, BlockPos, Optional<DisposalTubeRecord>> recordProvider = (level, pos) -> Optional.empty();
    public DisposalTubeBlock(BlockBehaviour.Properties properties) { super(properties); }
    /** Client presentation may provide already-synchronized ports without a common-side client dependency. */
    public static void setRecordProvider(BiFunction<Level, BlockPos, Optional<DisposalTubeRecord>> provider) {
        recordProvider = java.util.Objects.requireNonNull(provider);
    }
    private static VoxelShape shape(BlockGetter level, BlockPos pos) {
        if (!(level instanceof Level world)) return Shapes.empty();
        Optional<DisposalTubeRecord> record = world instanceof ServerLevel server
                ? DisposalTubeWorldData.existing(server).flatMap(data -> {
                    var lookup=data.lookup(server,pos);
                    return lookup.kind()==DisposalTubeWorldData.LookupKind.PRESENT ? Optional.of(lookup.record()) : Optional.empty();
                }) : recordProvider.apply(world,pos);
        if (record.isEmpty()) return Shapes.empty();
        ResourceLocation id=ResourceLocation.tryParse(record.get().prototypeId());
        if(id==null) return Shapes.empty();
        DisposalEntityData entity=(world.isClientSide ? PrototypeRuntime.clientDisposals() : PrototypeRuntime.serverDisposals()).get(id);
        return entity==null || entity.tube()==null ? Shapes.empty() : shapeFor(entity,record.get());
    }
    /** Shared exact resolved-prototype geometry path used by server collision and client outline. */
    public static VoxelShape shapeFor(DisposalEntityData entity, DisposalTubeRecord record) {
        if (entity == null || entity.tube() == null || record == null) return Shapes.empty();
        VoxelShape shape=Shapes.empty();
        for (AABB box : entity.geometry().boxes(entity.tube(),record.frame()))
            shape=Shapes.or(shape,Shapes.box(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ));
        return shape;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(level,pos); }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return shape(level,pos); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server)
            DisposalTubeWorldData.existing(server).ifPresent(data -> data.invalidate(server,pos));
        super.onRemove(state,level,pos,newState,movedByPiston);
    }
}
