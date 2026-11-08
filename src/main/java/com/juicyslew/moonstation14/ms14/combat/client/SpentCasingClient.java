package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.SpentCasingPayload;

/** Client presentation handoff; particle provider installs the consumer when registered. */
public final class SpentCasingClient {
    private SpentCasingClient() { }
    public static void receive(SpentCasingPayload payload) {
        var mc=net.minecraft.client.Minecraft.getInstance(); var level=mc.level;
        if(payload==null||level==null||!level.dimension().location().equals(payload.dimension()))return;
        var random=new java.util.Random(payload.seed());
        var options=new com.juicyslew.moonstation14.entities.SpentCasingParticleOptions(payload.sprite(),payload.landingEvent(),payload.lifetimeTicks(),payload.initialSpeed(),payload.gravity(),payload.damping(),payload.volumeDb(),payload.pitchVariation());
        for(int i=0;i<payload.count();i++){
            var d=payload.direction(); double speed=payload.initialSpeed()*(.85+random.nextDouble()*.3);
            level.addParticle(options,payload.position().x,payload.position().y,payload.position().z,
                    d.x*speed+(random.nextDouble()-.5)*.06,d.y*speed+.05+random.nextDouble()*.04,d.z*speed+(random.nextDouble()-.5)*.06);
        }
    }
}
