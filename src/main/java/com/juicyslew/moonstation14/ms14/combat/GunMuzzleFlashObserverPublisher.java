package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Publishes only after authoritative projectile publication succeeded. */
public final class GunMuzzleFlashObserverPublisher {
    private GunMuzzleFlashObserverPublisher() { }
    public static boolean eligible(GunMuzzleFlashPayload payload,java.util.UUID recipient){return payload!=null&&recipient!=null&&!payload.initiator().equals(recipient);}
    public static void publish(ServerPlayer player,LivingEntity body,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){
        if(player==null||body==null||effect==null||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var payload=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.MUZZLE,body.getUUID(),player.getUUID(),level.dimension().location(),position,direction,effect);
        PacketDistributor.sendToPlayersTrackingEntity(body,payload);
    }
    public static void publishImpact(LivingEntity shooter,net.minecraft.world.entity.Entity body,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){
        if(body==null||effect==null||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var payload=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.IMPACT,shooter==null?body.getUUID():shooter.getUUID(),shooter==null?body.getUUID():shooter.getUUID(),level.dimension().location(),position,direction,effect);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(body,payload);
    }
}
