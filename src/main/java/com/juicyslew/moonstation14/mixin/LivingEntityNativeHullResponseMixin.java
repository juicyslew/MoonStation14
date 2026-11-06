package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeNormalContactPolicy;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeHullContactEligibility;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFrameVelocity;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies selected Sable hull response after its priority-1000 native movement return. */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityNativeHullResponseMixin {
    @Unique private Object moonstation14$previousInfo;
    @Unique private Object moonstation14$lastConsumed;
    @Unique private boolean moonstation14$armed;
    @Unique private long moonstation14$travelTick;

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void moonstation14$capturePreviousInfo(Vec3 input, CallbackInfo callback) {
        LivingEntity actor = (LivingEntity) (Object) this;
        moonstation14$travelTick = actor.level() instanceof ServerLevel level ? level.getServer().getTickCount() : Long.MIN_VALUE;
        var actorPoint = actor.level() instanceof ServerLevel level
                ? LoadedFrameRayQuery.currentActorPoint(level, actor) : null;
        moonstation14$armed = eligible(actor) && actor instanceof EntityMovementExtension
                && NativeHullContactEligibility.worldPoint(actorPoint, actorPoint == null ? null : actorPoint.frameId());
        moonstation14$previousInfo = actor instanceof EntityMovementExtension movement
                ? movement.sable$getCollisionInfo() : null;
    }

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"), require = 1)
    private void moonstation14$applySelectedHullResponse(Vec3 input, CallbackInfo callback) {
        LivingEntity actor = (LivingEntity) (Object) this;
        Object previous = moonstation14$previousInfo;
        moonstation14$previousInfo = null;
        boolean armed = moonstation14$armed;
        moonstation14$armed = false;
        if (!armed || !eligible(actor) || !(actor.level() instanceof ServerLevel serverLevel)
                || !(actor instanceof EntityMovementExtension movement)) return;
        var afterPoint = LoadedFrameRayQuery.currentActorPoint(serverLevel, actor);
        if (!NativeHullContactEligibility.worldPoint(afterPoint,
                afterPoint == null ? null : afterPoint.frameId())) return;
        Object info = movement.sable$getCollisionInfo();
        if (!(info instanceof NativeSelectedContactAccess contacts)) return;
        Object consumed = moonstation14$lastConsumed;
        moonstation14$lastConsumed = info;
        var entries = contacts.moonstation14$selectedContacts();
        if (!NativeHullContactEligibility.fresh(info, previous, consumed,
                moonstation14$travelTick, serverLevel.getServer().getTickCount(), entries,
                contacts.moonstation14$selectedContactsOverflowed())) return;
        Vec3 incoming = null;
        Vec3 normal = null;
        double mostClosing = 0;
        Vec3 surface = null;
        for (var entry : entries) {
            if (entry.verticalCollision()) continue;
            Vec3 candidate = entry.normal().normalize();
            if (candidate.lengthSqr() < 1.0e-12) continue;
            if (normal != null && normal.dot(candidate) <= .999) return;
            normal = candidate;
            var point = LoadedFrameRayQuery.pointAtWorldPosition(serverLevel, entry.frameId(), entry.entityPosition());
            if (point == null) return;
            Vec3 velocity = SableFrameVelocity.pointVelocityAt(serverLevel, point);
            if (velocity == null) return;
            Vec3 captured = entry.entityVelocity();
            if (!finite(captured)) return;
            Vec3 candidateIncoming = captured.scale(20);
            double closing = candidateIncoming.subtract(velocity).dot(candidate);
            if (closing < mostClosing) {
                mostClosing = closing;
                incoming = candidateIncoming;
                surface = velocity;
            }
        }
        if (incoming != null && normal != null && surface != null) {
            actor.setDeltaMovement(NativeNormalContactPolicy.replaceNormal(incoming,
                    actor.getDeltaMovement().scale(20), surface, normal, 0).scale(.05));
            actor.hurtMarked = true;
        }
    }

    @Unique private static boolean eligible(LivingEntity actor) {
        return actor instanceof Mob mob && !(actor instanceof net.minecraft.world.entity.player.Player)
                && !actor.level().isClientSide && !mob.isPassenger() && !mob.noPhysics
                && !customOwner(mob);
    }

    @Unique private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    @Unique private static boolean customOwner(Mob mob) {
        return com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob)
                || mob instanceof MindControlledMob controlled && controlled.moonstation14$isMovementOwned()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep.isCustomPhysicalOwner(mob);
    }
}
