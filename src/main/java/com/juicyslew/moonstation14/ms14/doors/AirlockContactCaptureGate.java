package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Side-neutral identity handoff from the existing connected capture to its native observer. */
public final class AirlockContactCaptureGate {
    private static long epoch;
    private static int entityId=-1;
    private static UUID bodyId;
    private AirlockContactCaptureGate() { }
    public static synchronized void arm(long captureEpoch,int capturedEntityId,UUID capturedBodyId){
        epoch=captureEpoch;entityId=capturedEntityId;bodyId=capturedBodyId;
    }
    public static synchronized void reset(){epoch=0;entityId=-1;bodyId=null;}
    public static synchronized boolean activeFor(Entity entity){
        return entity!=null&&matches(epoch,entityId,bodyId,entity.getId(),entity.getUUID());
    }
    static boolean matches(long captureEpoch,int capturedEntityId,UUID capturedBodyId,int observedEntityId,UUID observedBodyId){
        return captureEpoch>0&&capturedEntityId==observedEntityId&&capturedBodyId!=null&&capturedBodyId.equals(observedBodyId);
    }
}
