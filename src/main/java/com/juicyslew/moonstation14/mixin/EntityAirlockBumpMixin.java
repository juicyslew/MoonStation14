package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import com.juicyslew.moonstation14.ms14.doors.AirlockBumpMoveAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityAirlockBumpMixin implements AirlockBumpMoveAccess {
    @Unique private AirlockBumpMoveAccess.Move moonstation14$airlockMove;
    @Unique private boolean moonstation14$airlockTravelActive;
    @Unique private java.util.List<com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity> moonstation14$worldDoorCandidates = java.util.List.of();

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void moonstation14$checkAirlockBump(MoverType moverType, Vec3 requested, CallbackInfo callbackInfo) {
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel) {
            moonstation14$worldDoorCandidates = AirlockBumpSystem.beforeMoveContacts(entity, requested);
            if (moonstation14$airlockTravelActive) {
                SubLevelEntityCollision.CollisionInfo previous = entity instanceof EntityMovementExtension movement
                        ? movement.sable$getCollisionInfo() : null;
                moonstation14$airlockMove = new AirlockBumpMoveAccess.Move(entity.getBoundingBox(), requested, previous);
            }
        }
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("TAIL"), require = 1)
    private void moonstation14$resolveOrdinaryBumpAgainstFinalNativePose(MoverType moverType, Vec3 requested, CallbackInfo callbackInfo) {
        Entity entity = (Entity) (Object) this;
        var nominated = moonstation14$worldDoorCandidates;
        moonstation14$worldDoorCandidates = java.util.List.of();
        if (entity.level() instanceof ServerLevel)
            AirlockBumpSystem.afterWorldMove(entity, nominated);
    }

    @Override public void moonstation14$beginAirlockTravel() {
        moonstation14$airlockMove = null;
        moonstation14$airlockTravelActive = true;
    }
    @Override public boolean moonstation14$isAirlockTravelActive() { return moonstation14$airlockTravelActive; }
    @Override public void moonstation14$setAirlockMove(AirlockBumpMoveAccess.Move move) { moonstation14$airlockMove = move; }
    @Override public AirlockBumpMoveAccess.Move moonstation14$getAirlockMove() { return moonstation14$airlockMove; }
    @Override public void moonstation14$clearAirlockMove() { moonstation14$airlockMove = null; moonstation14$airlockTravelActive = false; }
}
