package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.world.phys.Vec3;

/** Client-side, presentation-only prediction for one admitted gun press. */
public final class GunShotClient {
    private static final GunShotClientPolicy POLICY = new GunShotClientPolicy();
    private static final GunTriggerClientPolicy TRIGGER = new GunTriggerClientPolicy();
    private static final long TIMEOUT = 100;
    private static java.util.UUID boundBody;
    private static long boundEpoch;
    private GunShotClient() { }

    public static boolean request(net.minecraft.world.entity.Mob body, long epoch) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var selection = ClientGunWeaponResolver.resolve().orElse(null);
        var stack = selection == null ? null : ClientGunWeaponResolver.presentationStack(selection).orElse(null);
        if (body == null || selection == null || stack == null || mc.level == null || epoch <= 0) return false;
        var runtime = stack.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        String mode = GunModePolicy.effectiveFireMode(runtime, selection.profile());
        var context = new GunTriggerClientPolicy.Context(body.getUUID(), epoch,
                mc.level.dimension().location().toString(), selection.activeHand(), selection.itemToken(), mode);
        long triggerId = TRIGGER.begin(context);
        return attemptShot(body, epoch, triggerId, true);
    }

    private static boolean attemptShot(net.minecraft.world.entity.Mob body, long epoch, long triggerId, boolean start) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var selection = ClientGunWeaponResolver.resolve().orElse(null);
        if (body == null || selection == null || mc.level == null || !body.getUUID().equals(selection.body())
                || epoch <= 0 || !selection.equals(ClientGunWeaponResolver.resolve().orElse(null))) return false;
        if (com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(body)
                || !CombatModeClient.projectedMode()) return false;
        long now = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
        var proof = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        var stack = ClientGunWeaponResolver.presentationStack(selection).orElse(null);
        if (proof == null || proof.epoch() != epoch || !proof.bodyId().equals(body.getUUID())
                || estimate.isEmpty() || stack == null) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            return false;
        }
        if (!POLICY.snapshotFresh(body.getUUID(), epoch, selection.activeHand(), selection.itemToken(), proof.revision())) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            return false;
        }
        var runtime = stack.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        if (!MeleeAttackClientPolicy.sourceDeadlineReady(runtime.nextFireTick(), estimate)) return false;
        var gun = selection.profile();
        if (gun.requiresWield() && !ClientWieldProjection.confirms(proof, epoch, selection.activeHand(), selection.itemToken())) return false;
        GunAmmoProviderKernel.TakeResult plan;
        try {
            stack = new GunItemFactory(ModGunProfiles.clientCatalog(), ModAmmoContainerProfiles.clientCatalog(),
                    ModBatteryProfiles.clientCatalog()).initializeOwnedCopy(stack);
            plan = ClientGunShotPrediction.takeOne(stack, gun, runtime,
                    ModCartridgeProfiles.clientCatalog().asMap(), ModProjectileProfiles.clientCatalog().asMap(),
                    ModBatteryProfiles.clientCatalog().asMap());
        } catch (RuntimeException invalid) { return false; }
        if (plan.status() == GunAmmoProviderKernel.Status.INVALID) return false;
        if (!PacificationSystem.mayFire(PacificationSystem.isPacified(body), gun, runtime)) return false;
        POLICY.bind(body.getUUID(), epoch);
        var binding = new GunShotClientPolicy.Binding(selection.handRevision(), selection.activeHand(), selection.itemToken());
        var shotDeadline = GunFirePolicy.nextFireTick(now, gun.gun().fireRate());
        if (shotDeadline.isEmpty()) return false;
        long deadline = shotDeadline.getAsLong();
        if (plan.status() == GunAmmoProviderKernel.Status.EMPTY) {
            var empty = GunFirePolicy.emptyDeadline(now, deadline, gun.gun().burstCooldownSeconds());
            if (empty.isEmpty()) return false;
            deadline = empty.getAsLong();
        }
        var aim = body.getEyePosition().add(body.getLookAngle().scale(64));
        if (!Double.isFinite(aim.x) || !Double.isFinite(aim.y) || !Double.isFinite(aim.z)) return false;
        var request = POLICY.submit(binding, now, Math.max(1, deadline - now), aim, triggerId, start);
        if (request == null) return false;
        if (start) TRIGGER.submitted(triggerId);
        if (plan.status() == GunAmmoProviderKernel.Status.READY) {
            GunCameraRecoilClient.admittedShot(body.getUUID(), epoch, mc.level.dimension().location(), body.getLookAngle(), gun.gun().cameraRecoilScalar());
            var projectile = ModProjectileProfiles.clientCatalog().get(plan.projectile());
            var flash = projectile == null ? null : projectile.muzzleFlash();
            GunMuzzleFlashClient.enqueue(flash, body.getUUID(), epoch, mc.level.dimension().location(),
                    body.getEyePosition(), body.getLookAngle(), now,
                    new com.juicyslew.moonstation14.ms14.combat.ShotVisualSource(selection.activeHand(),
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()), request.sequence()));
        }
        var cue = ClientGunShotPrediction.feedbackCue(gun, plan.status()).orElse(null);
        if (cue != null) {
            var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(cue.event()).orElse(null);
            if (event != null) mc.level.playLocalSound(body.getX(), body.getY(), body.getZ(), event,
                    net.minecraft.sounds.SoundSource.PLAYERS, cue.volume(), MeleeSoundPolicy.pitch(1f, mc.level.random.nextFloat(), cue.pitchVariation()), false);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        return true;
    }

    public static void release() {
        sendStop(TRIGGER.release());
    }
    private static void sendStop(GunTriggerClientPolicy.Stop stop) {
        if (stop != null && net.minecraft.client.Minecraft.getInstance().getConnection() != null)
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new GunStopShootRequest(stop.epoch(), stop.id(), stop.hand(), stop.token()));
    }

    public static void handleReply(GunShotReply reply) {
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var body = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
            long epoch = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch();
            if (body == null || epoch <= 0) POLICY.clear(); else POLICY.bind(body.getUUID(), epoch);
            Long admittedTrigger = POLICY.pendingTrigger();
            boolean matched = POLICY.acknowledge(reply, com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
            if (matched && (reply.status() == GunFireSystem.Status.REJECTED
                    || reply.status() == GunFireSystem.Status.INVALID_PROFILE
                    || reply.status() == GunFireSystem.Status.CONSUMED_CANCELLED
                    || reply.status() == GunFireSystem.Status.CONSUMED_PUBLICATION_FAILED) && admittedTrigger != null)
                sendStop(TRIGGER.releaseIfMatches(admittedTrigger));
        });
    }
    public static void reconcile(net.minecraft.world.entity.Mob body, long epoch) {
        if (body == null || epoch <= 0) { release(); POLICY.clear(); boundBody = null; boundEpoch = 0; return; }
        if (!java.util.Objects.equals(boundBody, body.getUUID()) || boundEpoch != epoch) {
            release(); boundBody = body.getUUID(); boundEpoch = epoch;
        }
        POLICY.bind(body.getUUID(), epoch);
        long now = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        if (POLICY.timeout(now, TIMEOUT)) { release(); return; }
    }

    /** Heavy held-input polling runs once per logical client tick, never during reconciliation/render lookups. */
    public static void tickHeld(net.minecraft.world.entity.Mob body, long epoch) {
        if (body == null || epoch <= 0 || !java.util.Objects.equals(boundBody, body.getUUID()) || boundEpoch != epoch) return;
        long now = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock();
        var mc = net.minecraft.client.Minecraft.getInstance();
        var selection = ClientGunWeaponResolver.resolve().orElse(null);
        var stack = selection == null ? null : ClientGunWeaponResolver.presentationStack(selection).orElse(null);
        if (mc.level == null || selection == null || stack == null || !body.getUUID().equals(selection.body())) {
            release(); return;
        }
        var runtime = stack.getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        var current = new GunTriggerClientPolicy.Context(body.getUUID(), epoch, mc.level.dimension().location().toString(),
                selection.activeHand(), selection.itemToken(), GunModePolicy.effectiveFireMode(runtime, selection.profile()));
        var contextStop = TRIGGER.contextLost(current);
        if (contextStop != null) { sendStop(contextStop); return; }
        if (!TRIGGER.held()) return;
        long window = mc.getWindow().getWindow();
        if (!com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.gunHoldAdmissionAllowed(body)
                || org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) != org.lwjgl.glfw.GLFW.GLFW_PRESS
                || !CombatModeClient.projectedMode()) {
            release(); return;
        }
        var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
        var shot = TRIGGER.nextShot(current, POLICY.pending(), estimate.isPresent()
                && now >= POLICY.projectedDeadline()
                && MeleeAttackClientPolicy.sourceDeadlineReady(runtime.nextFireTick(), estimate));
        if (shot != null) attemptShot(body, epoch, shot.id(), shot.start());
    }
}
