package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.world.phys.Vec3;

/** Client-side, presentation-only prediction for one admitted gun press. */
public final class GunShotClient {
    private static final GunShotClientPolicy POLICY = new GunShotClientPolicy();
    private static final long TIMEOUT = 100;
    private GunShotClient() { }

    public static boolean request(net.minecraft.world.entity.Mob body, long epoch) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var selection = ClientGunWeaponResolver.resolve().orElse(null);
        if (body == null || selection == null || mc.level == null || !body.getUUID().equals(selection.body())
                || epoch <= 0 || !selection.equals(ClientGunWeaponResolver.resolve().orElse(null))) return false;
        if (com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(body)
                || !CombatModeClient.projectedMode()) return false;
        if (!PacificationSystem.mayFire(body, selection.profile())) return false;
        long now = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
        var proof = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        var stack = ClientGunWeaponResolver.presentationStack(selection).orElse(null);
        if (proof == null || proof.epoch() != epoch || !proof.bodyId().equals(body.getUUID())
                || estimate.isEmpty() || stack == null) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            return false;
        }
        var runtime = stack.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        if (!MeleeAttackClientPolicy.sourceDeadlineReady(runtime.nextFireTick(), estimate)) return false;
        var gun = selection.profile();
        if (gun.revolverAmmoProvider().isEmpty()) return false;
        var rounds = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        if (rounds == null) {
            try { rounds = RevolverProviderKernel.initialize(gun); }
            catch (RuntimeException invalid) { return false; }
        }
        var plan = RevolverProviderKernel.takeOne(rounds, gun, ModCartridgeProfiles.clientCatalog().asMap(),
                ModProjectileProfiles.clientCatalog().asMap());
        if (plan.status() == RevolverProviderKernel.TakeStatus.INVALID_PROFILE) return false;
        POLICY.bind(body.getUUID(), epoch);
        var binding = new GunShotClientPolicy.Binding(selection.handRevision(), selection.activeHand(), selection.itemToken());
        var shotDeadline = GunFirePolicy.nextFireTick(now, gun.gun().fireRate());
        if (shotDeadline.isEmpty()) return false;
        long deadline = shotDeadline.getAsLong();
        if (plan.status() == RevolverProviderKernel.TakeStatus.EMPTY) {
            var empty = GunFirePolicy.emptyDeadline(now, deadline, gun.gun().burstCooldownSeconds());
            if (empty.isEmpty()) return false;
            deadline = empty.getAsLong();
        }
        var aim = body.getEyePosition().add(body.getLookAngle().scale(64));
        if (!Double.isFinite(aim.x) || !Double.isFinite(aim.y) || !Double.isFinite(aim.z)) return false;
        var request = POLICY.submit(binding, now, Math.max(1, deadline - now), aim);
        if (request == null) return false;
        if (plan.status() == RevolverProviderKernel.TakeStatus.READY) {
            GunCameraRecoilClient.admittedShot(body.getUUID(), epoch, mc.level.dimension().location(), body.getLookAngle(), gun.gun().cameraRecoilScalar());
            var projectile = plan.shot().map(RevolverProviderKernel.ShotIntent::projectile).orElse(null);
            var flash = projectile == null ? null : projectile.muzzleFlash();
            GunMuzzleFlashClient.enqueue(flash, body.getUUID(), epoch, mc.level.dimension().location(),
                    body.getPosition(1f).add(0, body.getBbHeight() * .5, 0), body.getLookAngle(), now);
        }
        var cue = (plan.status() == RevolverProviderKernel.TakeStatus.EMPTY ? gun.gun().emptySound() : gun.gun().gunshotSound()).orElse(null);
        if (cue != null) {
            var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.event()).orElse(null);
            if (event != null) mc.level.playLocalSound(body.getX(), body.getY(), body.getZ(), event,
                    net.minecraft.sounds.SoundSource.PLAYERS, cue.volume(), MeleeSoundPolicy.pitch(1f, mc.level.random.nextFloat(), cue.pitchVariation()), false);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        return true;
    }

    public static void handleReply(GunShotReply reply) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if (body == null || epoch <= 0) POLICY.clear(); else POLICY.bind(body.getUUID(), epoch);
            POLICY.acknowledge(reply, com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
        });
    }
    public static void reconcile(net.minecraft.world.entity.Mob body, long epoch) {
        if (body == null || epoch <= 0) { POLICY.clear(); return; }
        POLICY.bind(body.getUUID(), epoch);
        POLICY.timeout(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock(), TIMEOUT);
    }
}
