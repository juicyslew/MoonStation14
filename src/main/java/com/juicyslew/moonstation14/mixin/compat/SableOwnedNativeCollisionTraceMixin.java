package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.function.Function;

/** Bounded observational trace of exact owned-character native collision correction. */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SableOwnedNativeCollisionTraceMixin {
    // Exact seam predicate: collide's first inverse call consumes sink.trackingPosition
    // through lastSubLevelPose; its first forward call is the paired subLevelPose base
    // transport (LVT subLevel/i/substeps). Later pose projections are not wrapped.
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformPositionInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0),
            remap = false, require = 1)
    private static Vector3d moonstation14$captureSelectedFrameCarrierPoint(Pose3d lastPose,
            Vector3d trackingPosition, Operation<Vector3d> original, @Local(argsOnly = true) Entity entity) {
        CurrentFrameCollisionScope.captureStartingWorldPoint(entity,trackingPosition);
        return original.call(lastPose,trackingPosition);
    }

    /** Select the exact start-basis passenger transport while leaving native own motion and SAT untouched. */
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformPosition(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", ordinal = 0),
            remap = false, require = 1)
    private static Vector3d moonstation14$selectStartingBasisCarrier(Pose3d selectedPose, Vector3d localPoint,
            Operation<Vector3d> original, @Local(argsOnly = true) Entity entity,
            @Local(name = "subLevel") SubLevel selectedFrame,
            @Local(name = "i") int substep, @Local(name = "substeps") int substeps) {
        Vector3d result=original.call(selectedPose,localPoint);
        return CurrentFrameCollisionScope.selectStartingBasisTransport(entity,selectedFrame,result,substep,substeps);
    }

    /** Owned Human motion already owns its displacement; suppress only Sable's second velocity source. */
    @ModifyVariable(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At("HEAD"), index = 2, argsOnly = true, remap = false, require = 1)
    private static Vec3 moonstation14$suppressOwnedInheritedVelocity(Vec3 velocityMotionMoj,
            Entity entity, Vec3 motion, Vec3 velocityMotion, LevelReusedVectors sink) {
        return CurrentFrameCollisionScope.effectiveVelocityMotion(entity, velocityMotionMoj);
    }
    /**
     * Sable's tryStepUp probe starts from entityBoundsCenter, although the actual
     * solver OBB is at entityBoundsCenter + collisionMotion. For the owned-body
     * path only, test the same in-flight position before applying Sable's existing
     * inward probe and step increments. This keeps generic Sable behavior intact.
    */
    @WrapOperation(method = "tryStepUp", at = @At(value = "INVOKE",
            target = "Lorg/joml/Vector3d;set(Lorg/joml/Vector3dc;)Lorg/joml/Vector3d;", ordinal = 0),
            remap = false, require = 1)
    private static Vector3d moonstation14$anchorOwnedStepProbe(Vector3d receiver, Vector3dc entityBoundsCenter,
            Operation<Vector3d> original, @Local(argsOnly = true) Entity entity,
            @Local(argsOnly = true) Vector3d collisionMotion) {
        Vector3d result = original.call(receiver, entityBoundsCenter);
        if (CurrentFrameCollisionScope.adaptsOwnedCharacterStepProbe(entity))
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedStepProbe
                    .offsetCandidate(result, collisionMotion);
        return result;
    }

    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            remap = false, require = 1)
    private static Object moonstation14$traceNativeSelection(Map<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> map,
            Object key, Function<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> factory, Operation<Object> original,
            Entity entity, net.minecraft.world.phys.Vec3 motion, net.minecraft.world.phys.Vec3 velocity,
            LevelReusedVectors sink, @Local(name = "subLevel") SubLevel frame,
            @Local(name = "maxBlockPos") BlockPos.MutableBlockPos selectedPos,
            @Local(name = "maxBlockState") BlockState selectedState,
             @Local(name = "maxMTV") Vector3d selectedMtv,
             @Local(name = "collisionMotion") Vector3d collisionMotion,
             @Local(name = "entityBoundsOBB") OrientedBoundingBox3d entityBounds,
             @Local(name = "subLevelPose") Pose3d subLevelPose,
             @Local(name = "maxAABB") BoundingBox3d selectedBounds,
             @Local(name = "verticalCollision") boolean verticalCollision,
             @Local(name = "i") int substep, @Local(name = "substeps") int substeps,
            @Local(name = "maxIter") int iteration) {
        Object result = original.call(map, key, factory);
        CurrentFrameCollisionScope.recordNativeCollisionSelection(entity, frame.getUniqueId(), selectedPos, selectedState,
                selectedMtv, collisionMotion, substep, substeps, iteration,
                 CurrentFrameCollisionScope.shouldCaptureNativeCollisionGeometry(entity, selectedMtv, verticalCollision)
                        ? com.juicyslew.moonstation14.ms14.shuttle.sable.NativeCollisionGeometry.capture(
                                 entityBounds, subLevelPose, selectedBounds, selectedMtv, collisionMotion, iteration, substeps)
                        : null, verticalCollision);
        return result;
    }

    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;add(Lorg/joml/Vector3dc;)Lorg/joml/Vector3d;"),
            remap = false, require = 1)
    private static Vector3d moonstation14$traceNativeApplied(Vector3d receiver, Vector3dc argument,
            Operation<Vector3d> original, Entity entity, net.minecraft.world.phys.Vec3 motion,
            net.minecraft.world.phys.Vec3 velocity, LevelReusedVectors sink,
            @Local(name = "collisionMotion") Vector3d collisionMotion,
            @Local(name = "maxMTV") Vector3d maxMtv) {
        Vector3d result = original.call(receiver, argument);
        if (receiver == collisionMotion && argument == maxMtv)
            CurrentFrameCollisionScope.recordNativeCollisionApplied(entity, receiver, argument);
        return result;
    }
}
