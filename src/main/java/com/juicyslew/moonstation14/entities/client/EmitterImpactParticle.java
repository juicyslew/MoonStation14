package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.entities.EmitterImpactParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;

/** Animated, fullbright billboard matching the inherited SS14 BulletImpactEffect. */
public final class EmitterImpactParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private EmitterImpactParticle(ClientLevel level, double x, double y, double z, int lifetime, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = lifetime;
        this.quadSize = com.juicyslew.moonstation14.entities.EmitterBoltImpactPolicy.spriteScale(32);
        this.setSprite(sprites.get(0, com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.FRAME_COUNT - 1));
    }
    @Override public void tick() {
        super.tick();
        if (com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.shouldRemoveAfterTick(age, lifetime)) {
            remove();
            return;
        }
        setSprite(sprites.get(com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.index(age),
                com.juicyslew.moonstation14.entities.EmitterImpactFramePolicy.FRAME_COUNT - 1));
    }
    @Override protected int getLightColor(float partialTick) { return 0x00F000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static final class Provider implements ParticleProvider<EmitterImpactParticleOptions> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(EmitterImpactParticleOptions options, ClientLevel level, double x, double y,
                double z, double xd, double yd, double zd) {
            return new EmitterImpactParticle(level, x, y, z, options.lifetimeTicks(), sprites);
        }
    }
}
