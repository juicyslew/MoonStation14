package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

/** Routes cosmetic casing instructions to observers and remote-controlled participant views. */
public final class SpentCasingObserverPublisher {
    private SpentCasingObserverPublisher() { }
    public static void publish(ServerPlayer actor, LivingEntity body, SpentCartridgePresentation presentation,
                               Vec3 sourcePosition, Vec3 aim, boolean leftHand, int count, long seed) {
        publish(actor,body,presentation,sourcePosition,aim,leftHand,count,seed,null);
    }
    public static void publish(ServerPlayer actor, LivingEntity body, SpentCartridgePresentation p,
                               Vec3 source, Vec3 aim, boolean leftHand, int count, long seed,
                               Consumer<SpentCasingPayload> sink) {
        if(actor==null||body==null||p==null||source==null||aim==null||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        Vec3 axis=aim.normalize(); Vec3 side=axis.cross(new Vec3(0,1,0)); if(side.lengthSqr()<1e-8)side=new Vec3(1,0,0);else side=side.normalize();
        if(leftHand)side=side.scale(-1);
        Vec3 outward=axis.scale(-.55).add(side).add(0,.18,0).normalize();
        Vec3 origin=SpentCasingOrigin.resolve(body.getEyePosition(),axis,leftHand);
        var payload=new SpentCasingPayload(body.getUUID(),level.dimension().location(),p.sprite(),p.landingCue().event(),origin,outward,
                count,seed,SpentCasingMotion.lifetimeTicks(p.lifetimeSeconds()),p.initialSpeed(),p.gravity(),p.damping(),
                p.landingCue().volumeDb(),p.landingCue().pitchVariation());
        for(ServerPlayer observer:level.players())
            if(com.juicyslew.moonstation14.ms14.audio.BodyViewAudioAudience.canHear(level,observer,origin.x,origin.y,origin.z,64)
                    ||CombatPresentationAudience.controlsParticipant(observer,body,null)) {
                if(sink==null) PacketDistributor.sendToPlayer(observer,payload); else sink.accept(payload);
            }
    }
}
