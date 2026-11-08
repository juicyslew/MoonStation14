package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Places the resolved root PA profile using the same transform and identities as runtime assembly. */
public final class ParticleAcceleratorFixturePlacement {
    private ParticleAcceleratorFixturePlacement() { }

    public record Placed(ParticleAcceleratorControlProfile profile, BlockPos controlPosition,
                         ParticleAcceleratorPartBlockEntity control,
                         Map<String, ParticleAcceleratorPartBlockEntity> parts, Map<String, BlockPos> positions) { }

    public static Placed place(ServerLevel level, BlockPos controlPosition, Rotation orientation) {
        var profile = ModParticleAccelerators.catalog(level).get(ModParticleAccelerators.TYPE.typeId());
        if (profile == null) throw new IllegalStateException("resolved root PA profile unavailable");
        // Validate all roles before changing the world; unknown profiles must not leave partial fixtures.
        for (var entry : profile.parts().entrySet()) blockFor(entry.getKey());
        level.setBlock(controlPosition, ModBlocks.PA_CONTROL_BOX.get().defaultBlockState()
                .setValue(ParticleAcceleratorPartBlock.FACING, direction(orientation)), 3);
        Map<String, ParticleAcceleratorPartBlockEntity> parts = new LinkedHashMap<>();
        Map<String, BlockPos> positions = new LinkedHashMap<>();
        for (var entry : profile.parts().entrySet()) {
            var part = entry.getValue();
            BlockPos pos = controlPosition.offset(ParticleAcceleratorAssembly.toWorldVec(part.offsetX(), part.offsetY(), orientation));
            Rotation facing = orientation.getRotated(part.relativeRotation());
            level.setBlock(pos, blockFor(entry.getKey()).defaultBlockState().setValue(ParticleAcceleratorPartBlock.FACING, direction(facing)), 3);
            positions.put(entry.getKey(), pos);
            if (level.getBlockEntity(pos) instanceof ParticleAcceleratorPartBlockEntity entity) parts.put(entry.getKey(), entity);
        }
        if (!(level.getBlockEntity(controlPosition) instanceof ParticleAcceleratorPartBlockEntity control))
            throw new IllegalStateException("registered PA control adapter unavailable");
        return new Placed(profile, controlPosition, control, Map.copyOf(parts), Map.copyOf(positions));
    }

    private static com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock blockFor(String role) {
        return switch (role) {
            case "control" -> ModBlocks.PA_CONTROL_BOX.get(); case "end" -> ModBlocks.PA_END_CAP.get();
            case "fuel" -> ModBlocks.PA_FUEL_CHAMBER.get(); case "power" -> ModBlocks.PA_POWER_BOX.get();
            case "port" -> ModBlocks.PA_EMITTER_PORT.get(); case "fore" -> ModBlocks.PA_EMITTER_FORE.get();
            case "starboard" -> ModBlocks.PA_EMITTER_STARBOARD.get();
            default -> throw new IllegalStateException("unexpected resolved PA role " + role);
        };
    }
    private static Direction direction(Rotation rotation) {
        return switch (rotation) { case NONE -> Direction.SOUTH; case CLOCKWISE_90 -> Direction.WEST;
            case CLOCKWISE_180 -> Direction.NORTH; case COUNTERCLOCKWISE_90 -> Direction.EAST; };
    }
}
