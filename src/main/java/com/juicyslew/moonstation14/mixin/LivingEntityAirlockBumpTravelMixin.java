package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import com.juicyslew.moonstation14.ms14.doors.AirlockBumpMoveAccess;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Generic native LivingEntity route; Sable's priority-1000 carry return precedes this priority-900 observer. */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityAirlockBumpTravelMixin {
    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"), require = 1)
    private void moonstation14$consumeFreshNativeDoorContact(Vec3 input, CallbackInfo callback) {
        LivingEntity actor = (LivingEntity) (Object) this;
        if (!(actor.level() instanceof ServerLevel)) return;
        boolean ownedMob = actor instanceof Mob mob && (mob instanceof MindControlledMob controlled
                && controlled.moonstation14$isMovementOwned()
                || com.juicyslew.moonstation14.ms14.player_body_control.character.OwnedCorpsePhysics.eligible(mob));
        if (ownedMob) return;
        if (actor.isRemoved() || actor.noPhysics) {
            AirlockBumpSystem.noNativeContact(actor);
            return;
        }
        if (!(actor instanceof AirlockBumpMoveAccess access)) {
            AirlockBumpSystem.noNativeContact(actor);
            return;
        }
        AirlockBumpMoveAccess.Move move = access.moonstation14$getAirlockMove();
        access.moonstation14$clearAirlockMove();
        if (move == null || !(actor instanceof EntityMovementExtension movement)) {
            AirlockBumpSystem.noNativeContact(actor);
            return;
        }
        var collision = movement.sable$getCollisionInfo();
        if (collision == null || collision == move.previousInfo()) {
            AirlockBumpSystem.noNativeContact(actor);
            return;
        }
        AirlockBumpSystem.afterNativeMove(actor, move.beforeBounds(), move.requested(), collision);
    }

    @Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void moonstation14$beginAirlockTravel(Vec3 input, CallbackInfo callback) {
        LivingEntity actor = (LivingEntity) (Object) this;
        if (actor.level() instanceof ServerLevel && actor instanceof AirlockBumpMoveAccess access)
            access.moonstation14$beginAirlockTravel();
    }
}
