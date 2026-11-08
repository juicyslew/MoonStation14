package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Publishes only after authoritative projectile publication succeeded. */
public final class GunMuzzleFlashObserverPublisher {
    private GunMuzzleFlashObserverPublisher() { }
    public static boolean eligible(GunMuzzleFlashPayload payload,java.util.UUID recipient){return payload!=null&&recipient!=null&&(payload.kind()!=GunMuzzleFlashPayload.Kind.MUZZLE||!payload.initiator().equals(recipient));}
    public static void publish(ServerPlayer player,LivingEntity body,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){
        publish(player,body,effect,position,direction,null);
    }
    public static void publish(ServerPlayer player,LivingEntity body,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction,ShotVisualSource source){
        if(player==null||body==null||effect==null||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var payload=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.MUZZLE,body.getUUID(),player.getUUID(),level.dimension().location(),position,direction,effect,null,source);
        PacketDistributor.sendToPlayersTrackingEntity(body,payload);
    }
    public static void publishImpact(LivingEntity shooter,net.minecraft.world.entity.Entity body,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){
        if(body==null||effect==null||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var payload=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.IMPACT,shooter==null?body.getUUID():shooter.getUUID(),shooter==null?body.getUUID():shooter.getUUID(),level.dimension().location(),position,direction,effect);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(body,payload);
    }
    public static void publishImpact(LivingEntity shooter,net.minecraft.world.level.Level level,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){publishPoint(GunMuzzleFlashPayload.Kind.IMPACT,shooter,level,effect,position,direction);}
    public static void publishBeam(LivingEntity shooter,Vec3 origin,Vec3 endpoint,ProjectileProfile.ProjectileSpriteEffectData effect){publishBeam(shooter,origin,endpoint,effect,null);}
    public static void publishBeam(LivingEntity shooter,Vec3 origin,Vec3 endpoint,ProjectileProfile.ProjectileSpriteEffectData effect,ShotVisualSource source){if(shooter==null||!(shooter.level() instanceof net.minecraft.server.level.ServerLevel level)||effect==null)return;var payload=new GunMuzzleFlashPayload(GunMuzzleFlashPayload.Kind.BEAM,shooter.getUUID(),shooter.getUUID(),level.dimension().location(),origin,endpoint.subtract(origin),effect,endpoint,source);publishToAudience(level,shooter,null,origin,endpoint,payload);}
    private static void publishPoint(GunMuzzleFlashPayload.Kind kind,LivingEntity shooter,net.minecraft.world.level.Level world,ProjectileProfile.ProjectileSpriteEffectData effect,Vec3 position,Vec3 direction){if(!(world instanceof net.minecraft.server.level.ServerLevel level)||effect==null)return;var payload=new GunMuzzleFlashPayload(kind,shooter==null?new java.util.UUID(0,0):shooter.getUUID(),shooter==null?new java.util.UUID(0,0):shooter.getUUID(),level.dimension().location(),position,direction,effect);publishToAudience(level,shooter,null,position,position,payload);}
    private static void publishToAudience(net.minecraft.server.level.ServerLevel level,LivingEntity shooter,LivingEntity target,Vec3 start,Vec3 end,GunMuzzleFlashPayload payload){var recipients=new java.util.LinkedHashSet<ServerPlayer>();for(ServerPlayer player:level.players()){if(player.distanceToSqr(start)<=64*64||player.distanceToSqr(end)<=64*64||CombatPresentationAudience.controlsParticipant(player,shooter,target))recipients.add(player);}recipients.forEach(p->PacketDistributor.sendToPlayer(p,payload));}
}
