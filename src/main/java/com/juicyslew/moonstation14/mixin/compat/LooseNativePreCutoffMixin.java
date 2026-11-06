package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope;
import java.util.function.Supplier;

/** Read-only observation at Sable's inherited-motion small-vector cutoff. */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class LooseNativePreCutoffMixin {
    @WrapMethod(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;")
    private static SubLevelEntityCollision.CollisionInfo moonstation14$traceLooseStockCollision(Entity entity,
            Vec3 motion, Vec3 velocity, dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            Operation<SubLevelEntityCollision.CollisionInfo> original) {
        if (!(entity instanceof LooseItemEntity loose) || !loose.hasPendingNativeMoveCapture())
            return original.call(entity, motion, velocity, sink);
        var scoped = CurrentFrameCollisionScope.withDiagnosticStockTrace(entity,
                () -> original.call(entity, motion, velocity, sink));
        loose.observeNativeCorrectionTrace(scoped.value(), scoped.nativeCollisionTrace(), scoped.nativeCollisionTraceComplete());
        return scoped.value();
    }

    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"), remap = false, require = 1)
    private static double moonstation14$capturePreCutoff(Vec3 inherited, Operation<Double> original,
            Entity entity, Vec3 motion, Vec3 velocity, dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            @Local(name = "collisionInfo") SubLevelEntityCollision.CollisionInfo info,
            @Local(name = "entityBoundsOBB") OrientedBoundingBox3d solvedBox) {
        if (entity instanceof LooseItemEntity loose && loose.observesNativePreCutoff() && !entity.level().isClientSide) {
            var frame = info.trackingSubLevel;
            var pose = frame == null ? null : frame.logicalPose();
            var center = solvedBox.getPosition();
            var dimensions = solvedBox.getDimensions();
            var orientation = solvedBox.getOrientation();
            loose.observeNativePreCutoff(info, frame == null ? null : frame.getUniqueId(), pose,
                    center.x, center.y, center.z, dimensions.x, dimensions.y, dimensions.z, orientation, inherited);
        }
        return original.call(inherited);
    }
}
