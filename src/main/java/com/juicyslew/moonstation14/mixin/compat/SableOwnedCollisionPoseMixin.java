package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Scoped native collision pose correction for exact owned CHARACTER moves.
 * Pinned Sable collide() reads sink.subLevelPose for getHitBoxYaw at source line 175, before its candidate
 * loop overwrites that reused LevelReusedVectors field at lines 200-208. The hitbox yaw therefore can be
 * inherited from an unrelated prior entity/frame. The owned path uses CollisionInfo.trackingSubLevel and
 * the current logical pose or that frame's own last-to-logical endpoint interpolation; unscoped and
 * STOCK_SDK calls retain original SDK behavior. Geometry remains native Sable collision, not a replacement backend.
 * Pinned collide() also publishes the raw tracked-body temporary delta, then nulls inheritedMotion when
 * lengthSqr() is below 1e-8 at source lines 442-447. Only a verified live owned-character stock interval
 * with distinct last/logical poses may retain that exact finite raw delta; other paths keep the SDK cutoff.
 * This is a Minecraft owned-character adapter departure; SDK/Rapier constants, prototype values, and native solver remain unchanged.
 */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SableOwnedCollisionPoseMixin {
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/SubLevel;logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"), remap = false, require = 1)
    private static dev.ryanhcode.sable.companion.math.Pose3d moonstation14$temporalLogicalPose(SubLevel subLevel,
            Operation<dev.ryanhcode.sable.companion.math.Pose3d> original, Entity entity,
            net.minecraft.world.phys.Vec3 motion, net.minecraft.world.phys.Vec3 velocity,
            dev.ryanhcode.sable.api.math.LevelReusedVectors sink) {
        var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.active(entity.level(), subLevel);
        if(temporal!=null)return (dev.ryanhcode.sable.companion.math.Pose3d) temporal.to(subLevel);
        return original.call(subLevel);
    }
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/SubLevel;lastPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;"),
            remap = false, require = 1)
    private static Pose3dc moonstation14$currentOwnedCollisionPose(SubLevel subLevel, Operation<Pose3dc> original,
                                                                      Entity entity, net.minecraft.world.phys.Vec3 motion,
                                                                      net.minecraft.world.phys.Vec3 velocity,
                                                                      dev.ryanhcode.sable.api.math.LevelReusedVectors sink) {
        var temporal = com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope.active(entity.level(), subLevel);
        if(temporal!=null)return temporal.from(subLevel);
        if (CurrentFrameCollisionScope.usesCurrentGeometry(entity)) return subLevel.logicalPose();
        return original.call(subLevel);
    }

    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision;getHitBoxYaw(Ldev/ryanhcode/sable/companion/math/Pose3dc;)D"),
            remap = false, require = 1)
    private static double moonstation14$trackedCharacterBodyYaw(Pose3dc sdkSinkPose,
            Operation<Double> original, Entity entity, net.minecraft.world.phys.Vec3 motion,
            net.minecraft.world.phys.Vec3 velocity,
            dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            @Local(name = "collisionInfo") SubLevelEntityCollision.CollisionInfo collisionInfo,
            @Local(name = "i") int substep, @Local(name = "substeps") int substeps) {
        if (!CurrentFrameCollisionScope.usesTrackedCharacterYaw(entity)) return original.call(sdkSinkPose);
        CurrentFrameCollisionScope.observeSdkYawSource(entity, sdkSinkPose);
        double sdkYaw = original.call(sdkSinkPose);
        Pose3dc bodyPose = CurrentFrameCollisionScope.trackedCharacterYawPose(entity, collisionInfo, substep, substeps);
        Pose3dc selectedPose = bodyPose == null ? new dev.ryanhcode.sable.companion.math.Pose3d() : bodyPose;
        double selectedYaw = SubLevelEntityCollision.getHitBoxYaw(selectedPose);
        CurrentFrameCollisionScope.recordYawSample(entity, collisionInfo, substep, substeps,
                sdkSinkPose, sdkYaw, selectedPose, selectedYaw);
        return selectedYaw;
    }

    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"),
            remap = false, require = 1)
    private static double moonstation14$preserveOwnedIntervalMicroCarry(net.minecraft.world.phys.Vec3 rawNativeDelta,
            Operation<Double> original, Entity entity, net.minecraft.world.phys.Vec3 motion,
            net.minecraft.world.phys.Vec3 velocity, dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            @Local(name = "collisionInfo") SubLevelEntityCollision.CollisionInfo collisionInfo) {
        if (CurrentFrameCollisionScope.retainOwnedCharacterMicroCarry(entity, collisionInfo, rawNativeDelta))
            return 1.0e-8d;
        return original.call(rawNativeDelta);
    }
}
