package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Predicate;

/** Shared live doorway obstruction query for admitted door closing. */
public final class DoorCloseAdmission {
    private DoorCloseAdmission() { }
    public static List<LivingEntity> obstructingBodies(ServerLevel level,AABB doorway,Predicate<LivingEntity> pairCollision){
        return level.getEntitiesOfClass(LivingEntity.class,doorway,actor->!actor.isSpectator()&&!actor.noPhysics
                &&com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(actor,level)
                &&actor.getBoundingBox().intersects(doorway)&&pairCollision.test(actor));
    }
}
