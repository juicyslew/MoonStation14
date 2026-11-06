package com.juicyslew.moonstation14.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.juicyslew.moonstation14.ms14.shuttle.sable.OwnedFrameSimulationScope;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Temporal candidate union before Sable narrowphase; bounded by loaded-frame count. */
@Mixin(targets="dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision", remap=false)
public abstract class SableTemporalCollisionCandidatesMixin {
    @WrapOperation(method="collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/ActiveSableCompanion;getAllIntersecting(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"), remap=false, require=1)
    private static Iterable<SubLevel> moonstation14$temporalCandidates(ActiveSableCompanion helper, Level level,
            BoundingBox3dc query, Operation<Iterable<SubLevel>> original, Entity entity,
            net.minecraft.world.phys.Vec3 motion, net.minecraft.world.phys.Vec3 velocity, LevelReusedVectors sink) {
        var scope=OwnedFrameSimulationScope.activeFor(level);
        if(scope==null||scope.actor()!=entity)return original.call(helper,level,query);
        var selected=scope.candidates(level,query,original.call(helper,level,query));
        if(!scope.ready())throw new OwnedFrameSimulationScope.TemporalUnavailableException(scope.failure());
        return selected;
    }
}
