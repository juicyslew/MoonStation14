package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side arc presentation dispatch and supplemental endpoint sparks. */
public final class TeslaLightningEffects {
    private TeslaLightningEffects() { }
    public static void emit(ServerLevel level, Vec3 from, Vec3 to, double lifetimeSeconds) {
        emit(level, from, to, lifetimeSeconds, 0);
    }
    public static void emit(ServerLevel level, Vec3 from, Vec3 to, double lifetimeSeconds, double beamHalfWidth) {
        if(level==null||from==null||to==null||!Double.isFinite(lifetimeSeconds)||lifetimeSeconds<=0) return;
        TeslaArcNetworking.send(level,from,to,level.random.nextLong(),lifetimeSeconds,beamHalfWidth);
        int samples=Math.max(2,Math.min(32,(int)Math.ceil(from.distanceTo(to)*4)));
        for(int i=0;i<=samples;i++) {
            double t=(double)i/samples;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t,
                    from.z+(to.z-from.z)*t,1,0,0,0,0);
        }
        level.playSound(null, to.x, to.y, to.z,
                com.juicyslew.moonstation14.sounds.ModSounds.TESLA_LIGHTNING_SHOCK.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
    }

}
