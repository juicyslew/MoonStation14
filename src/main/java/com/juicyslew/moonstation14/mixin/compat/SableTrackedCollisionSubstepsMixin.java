package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope;
import com.juicyslew.moonstation14.ms14.shuttle.sable.TrackedCollisionSubsteps;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeFrameInterval;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.sugar.Local;

import it.unimi.dsi.fastutil.objects.ObjectSet;

@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SableTrackedCollisionSubstepsMixin {
    @ModifyVariable(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "STORE", ordinal = 0), index = 18, remap = false, require = 1)
    private static int moonstation14$relativeTrackedSubsteps(int stock, Entity entity, Vec3 motion, Vec3 velocity,
            dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            @Local(name = "intersecting") ObjectSet<SubLevel> intersecting) {
        if (!CurrentFrameCollisionScope.usesTrackedCharacterYaw(entity)
                || CurrentFrameCollisionScope.usesCurrentGeometry(entity)) return stock;
        double sweep = Math.sqrt(motion.lengthSqr());
        Vec3 feet = entity.position();
        for (SubLevel frame : intersecting) {
            if (frame == null || frame.isRemoved() || frame.getLevel() != entity.level() || frame.getPlot() == null
                    || SubLevelContainer.getContainer(entity.level()).getSubLevel(frame.getUniqueId()) != frame) continue;
            try {
                var logical = frame.logicalPose();
                var last = new dev.ryanhcode.sable.companion.math.Pose3d(frame.lastPose());
                if (last.rotationPoint().lengthSquared() <= 0.0) last.rotationPoint().set(logical.rotationPoint());
                NativeFrameInterval.PosePair.capture(last, logical);
                var local = last.transformPositionInverse(new org.joml.Vector3d(feet.x, feet.y, feet.z));
                var before = last.transformPosition(new org.joml.Vector3d(local));
                var after = logical.transformPosition(local);
                double relative = TrackedCollisionSubsteps.relativeSweep(motion.x, motion.y, motion.z,
                        after.x - before.x, after.y - before.y, after.z - before.z);
                if (Double.isFinite(relative)) sweep = Math.max(sweep, relative);
            } catch (RuntimeException ignored) {
                // An unusable candidate cannot authorize guessed frame motion.
            }
        }
        return TrackedCollisionSubsteps.required(stock, sweep);
    }
}
