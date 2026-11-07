package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.server.level.ServerLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Compatibility facade: topology reconciliation is separate from gas exchange. */
public final class LoadedPipeEqualization {
    private LoadedPipeEqualization() { }

    /** Returns false without mutation whenever the complete live component cannot be verified. */
    public static boolean equalize(ServerLevel level, VentilationDeviceBlockEntity device) {
        if (level == null || device == null || level.isClientSide || device.getLevel() != level
                || !level.getServer().isSameThread() || !level.hasChunkAt(device.getBlockPos())
                || level.getBlockEntity(device.getBlockPos()) != device
                || !(level.getBlockState(device.getBlockPos()).getBlock() instanceof VentilationDeviceBlock)
                || !device.settingsValid()) return false;
        if (!LoadedPipeNetworks.reconcile(level)) return false;
        Direction facing=level.getBlockState(device.getBlockPos()).getValue(VentilationDeviceBlock.FACING);
        BlockPos pipePos=device.getBlockPos().relative(facing.getOpposite());
        if(!level.hasChunkAt(pipePos))return false;
        var settings=device.config().settings();
        int lane=switch(settings.selectedLayer()) {
            case PRIMARY -> VisualPipeBlockEntity.PRIMARY;
            case SECONDARY -> VisualPipeBlockEntity.SECONDARY;
            case TERTIARY -> VisualPipeBlockEntity.TERTIARY;
        };
        if(level.getBlockEntity(pipePos) instanceof VisualPipeBlockEntity visual) {
            return visual.hasLayer(lane)
                    &&PipeGasInventoryEndpoint.create(level,pipePos,lane,facing).isPresent();
        }
        return PipeGasInventoryEndpoint.createSealed(level,pipePos,
                PipeTopology.Layer.valueOf(settings.selectedLayer().name()),facing).isPresent();
    }

}

