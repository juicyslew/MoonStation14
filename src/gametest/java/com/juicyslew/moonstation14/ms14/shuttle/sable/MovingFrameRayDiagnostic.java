package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** Focused failure-only evidence for the production loaded/frame ray query. */
final class MovingFrameRayDiagnostic {
    private MovingFrameRayDiagnostic() { }

    static String describe(ServerLevel level, LivingEntity body, BlockPos nominated) {
        var target = LoadedFrameRayQuery.currentTargetPoint(level, nominated);
        Vec3 from = body.getEyePosition();
        Vec3 to = from.add(body.getLookAngle().scale(BodyBlockUseTargetProbe.MAX_REACH));
        var raw = LoadedFrameRayQuery.clip(level, from, to, 16, .125, 1,
                (getter, rayFrom, rayTo) -> new ClipContext(rayFrom, rayTo,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body) {
                    @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(
                            net.minecraft.world.level.block.state.BlockState state,
                            net.minecraft.world.level.BlockGetter ignored, BlockPos pos) {
                        return super.getBlockShape(state, level, pos);
                    }
                });
        StringBuilder probes = new StringBuilder();
        for (Direction face : Direction.values()) {
            if (!probes.isEmpty()) probes.append(',');
            probes.append(face).append('=').append(BodyBlockUseTargetProbe.probe(level, body, nominated, face));
        }
        return "feet=" + body.position() + ", eye=" + from + ", look=" + body.getLookAngle()
                + ", yaw=" + body.getYRot() + ", pitch=" + body.getXRot()
                + ", storageTarget=" + nominated + ", currentTarget=" + target
                + ", rawRayState=" + raw.state() + ", rawFrameHit=" + raw.frameHit()
                + ", rawWorldHit=" + raw.worldHit() + ", nominatedFaceProbes={" + probes + "}";
    }
}
