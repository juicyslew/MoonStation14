package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.entities.TypedProjectileEntity;
import com.juicyslew.moonstation14.ms14.combat.MeleeSoundPolicy;
import com.juicyslew.moonstation14.ms14.combat.ProjectilePresentationMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Local-only fly-by presentation around the current owned body. */
public final class ClientFlyByFeedback {
    private ClientFlyByFeedback() {}
    public static void install() { TypedProjectileEntity.installFlyByPresentationCallback(ClientFlyByFeedback::present); }
    static void present(TypedProjectileEntity projectile, Vec3 previous, Vec3 current,
                        com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.FlyByData descriptor) {
        var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        if (body == null || body.isRemoved() || body.level() != projectile.level()) {
            projectile.setWasInsideFlyByRange(false);
            return;
        }
        double range = descriptor.range();
        Vec3 center = body.position().add(0, body.getBbHeight() * .5, 0);
        boolean inside = Double.isFinite(range) && range >= 0
                && ProjectilePresentationMotion.closestSegmentDistanceSquared(previous, current, center) <= range * range;
        boolean own = projectile.getOwner() != null && projectile.getOwner().getUUID().equals(body.getUUID());
        boolean play = FlyByPresentationPolicy.entered(projectile.wasInsideFlyByRange(), inside, own,
                descriptor.probability(), RandomSource.create().nextFloat());
        projectile.setWasInsideFlyByRange(inside);
        if (!play) return;
        var cue = descriptor.cue();
        var mc = Minecraft.getInstance();
        var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.event()).orElse(null);
        if (sound == null || mc.level == null || body.level() != mc.level) return;
        mc.level.playLocalSound(body.getX(), body.getY(), body.getZ(), sound, SoundSource.PLAYERS,
                cue.volume(), MeleeSoundPolicy.pitch(1f, RandomSource.create().nextFloat(), cue.pitchVariation()), false);
    }
}
