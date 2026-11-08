package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import com.juicyslew.moonstation14.entities.TypedProjectileEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import net.neoforged.neoforge.common.NeoForge;

/** Server-owned one-press gun consumer; owned ammunition/cadence publishes before the projectile. */
public final class GunFireSystem {
    public enum Status { REJECTED, COOLDOWN, INVALID_PROFILE, EMPTY, FIRED, CONSUMED_CANCELLED, CONSUMED_PUBLICATION_FAILED }
    public record Result(Status status, GunWeaponResolver.Selection selection, long nextFireTick) { }
    private GunFireSystem() { }

    public static Result fire(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                              GunWeaponResolver.Selection expected, Vec3 nominatedAimPoint) {
        if (player == null || authority == null || expected == null || nominatedAimPoint == null
                || !(player.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || !BodyActionAuthority.revalidate(player, authority)) return rejected();
        var body = authority.body();
        if (!body.isAlive() || !CharacterControlSystem.canAct(body) || !CombatModeSystem.isInCombatMode(body)
                || !GunWeaponResolver.revalidate(body, expected)) return rejected();
        Vec3 eye = body.getEyePosition();
        Vec3 toward = nominatedAimPoint.subtract(eye);
        if (!finite(nominatedAimPoint) || !finite(eye) || !finite(toward) || toward.lengthSqr() < 1.0e-12) return rejected();

        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || !expected.activeHand().equals(hands.activeHand())) return rejected();
        var binding = new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD, expected.activeHand(),
                hands.revision(), expected.itemToken());
        var owned = GunWeaponResolver.readOwned(body, expected).orElse(null);
        if (owned == null) return rejected();
        var eventGun = new GunAttackEvents.OwnedGunBinding(binding, expected);
        if (!GunAttackEvents.dispatchShotAttempted(body, eventGun, NeoForge.EVENT_BUS::post)) return rejected();
        if (!BodyActionAuthority.revalidate(player, authority) || !CharacterControlSystem.canAct(body)
                || !CombatModeSystem.isInCombatMode(body) || !GunWeaponResolver.revalidate(body, expected)
                || LiveOwnedItemAccess.read(body, binding).isEmpty()) return rejected();
        ItemStack staged = owned.stack();
        GunRuntimeState runtime = staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(), GunRuntimeState.INITIAL);
        long now = level.getGameTime();
        if (now < runtime.nextFireTick()) return result(Status.COOLDOWN, expected, runtime.nextFireTick());
        var nextTick = GunFirePolicy.nextFireTick(now, expected.profile().gun().fireRate());
        if (nextTick.isEmpty()) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
        long deadline = nextTick.getAsLong();
        boolean allowedToShoot = GunAttackEvents.dispatchAttemptShoot(body, eventGun, deadline, NeoForge.EVENT_BUS::post);
        if (!allowedToShoot) {
            staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(), runtime.withFire(deadline, runtime.lastFireTick(), runtime.currentSpreadDegrees()));
            if (!commit(player, authority, body, expected, owned, staged)) return rejected();
            return result(Status.CONSUMED_CANCELLED, GunWeaponResolver.resolve(body).orElse(null), deadline);
        }
        var nextRuntime = runtime.withFire(deadline, runtime.lastFireTick(), runtime.currentSpreadDegrees());

        var gun = expected.profile();
        if (gun.revolverAmmoProvider().isEmpty()) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
        RevolverState rounds = staged.get(ModDataComponents.REVOLVER_STATE.get());
        if (rounds == null) rounds = RevolverProviderKernel.initialize(gun);
        var plan = RevolverProviderKernel.takeOne(rounds, gun, ModCartridgeProfiles.serverCatalog().asMap(),
                ModProjectileProfiles.serverCatalog().asMap());
        if (plan.status() == RevolverProviderKernel.TakeStatus.INVALID_PROFILE)
            return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());

        TypedProjectileEntity projectile = null;
        if (plan.status() == RevolverProviderKernel.TakeStatus.EMPTY) {
            var safeDeadline = GunFirePolicy.emptyDeadline(now, deadline, gun.gun().burstCooldownSeconds());
            if (safeDeadline.isEmpty()) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
            long safe = safeDeadline.getAsLong();
            nextRuntime = runtime.withFire(safe, runtime.lastFireTick(), runtime.currentSpreadDegrees());
        } else {
            var intent = plan.shot().orElseThrow();
            var spread = GunFirePolicy.recoil(runtime, gun.gun().spread(), now, level.getRandom().nextFloat() * 2f - 1f);
            nextRuntime = runtime.withFire(deadline, deadline, spread.currentSpreadDegrees());
            if (intent.physical().isEmpty() && !intent.deleteOnSpawn()) {
                ItemStack spent = materializeCartridge(intent.cartridgePrototype(), true);
                if (spent == null) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
                var chambers = new java.util.ArrayList<>(plan.state().chambers());
                chambers.set(intent.chamberIndex(), new RevolverState.Chamber(false, Optional.of(spent)));
                plan = new RevolverProviderKernel.TakeResult(new RevolverState(plan.state().index(), chambers), plan.status(), plan.shot());
            }
            Vec3 velocity = projectileVelocity(toward, spread.angleDegrees(), gun.gun().projectileSpeed(), body.getDeltaMovement());
            if (!finite(velocity) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(eye))) return rejected();
            projectile = TypedProjectileEntity.create(level, intent.projectilePrototype().toString(), intent.projectile(), body, eye, velocity);
        }

        staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(), nextRuntime);
        staged.set(ModDataComponents.REVOLVER_STATE.get(), plan.state());
        if (!commit(player, authority, body, expected, owned, staged)) return rejected();
        var refreshed = GunWeaponResolver.resolve(body).orElse(null);
        if (plan.status() == RevolverProviderKernel.TakeStatus.EMPTY) {
            var emptyEvent = new GunAttackEvents.EmptyGunShotEvent(body, eventGun);
            NeoForge.EVENT_BUS.post(emptyEvent);
            gun.gun().emptySound().ifPresent(cue -> PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS,
                    body.getX(), body.getY(), body.getZ(), cue.volume(), MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), cue.pitchVariation()), player.getUUID()));
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("gun-magazine-fired-empty"), true);
            return result(Status.EMPTY, refreshed, nextRuntime.nextFireTick());
        }
        try {
            if (!level.addFreshEntity(projectile)) return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
        } catch (RuntimeException denied) {
            return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
        }
        var flash=plan.shot().map(RevolverProviderKernel.ShotIntent::projectile).map(ProjectileProfile::muzzleFlash).orElse(null);
        GunMuzzleFlashObserverPublisher.publish(player,body,flash,eye,toward.normalize());
        gun.gun().gunshotSound().ifPresent(cue -> PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS,
                body.getX(), body.getY(), body.getZ(), cue.volume(), MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), cue.pitchVariation()), player.getUUID()));
        var shotEvent = new GunAttackEvents.GunShotEvent(body, eventGun);
        NeoForge.EVENT_BUS.post(shotEvent);
        return result(Status.FIRED, refreshed, nextRuntime.nextFireTick());
    }

    private static boolean commit(ServerPlayer player, BodyActionAuthority.Snapshot authority,
            net.minecraft.world.entity.LivingEntity body, GunWeaponResolver.Selection expected,
            LiveOwnedItemAccess.OwnerView owned, ItemStack replacement) {
        if (!BodyActionAuthority.revalidate(player, authority) || !GunWeaponResolver.revalidate(body, expected)) return false;
        return LiveOwnedItemAccess.commitComponents(owned, replacement,
                () -> BodyActionAuthority.revalidate(player, authority) && GunWeaponResolver.revalidate(body, expected)).isPresent();
    }

    static ItemStack materializeSpentCartridge(net.minecraft.resources.ResourceLocation id) {
        return materializeCartridge(id, true);
    }
    static ItemStack materializeCartridge(net.minecraft.resources.ResourceLocation id, boolean spent) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.CARTRIDGE_SPENT.get(), spent);
        return stack;
    }
    static Vec3 projectileVelocity(Vec3 toward, float angleDegrees, float projectileSpeed, Vec3 bodyVelocity) {
        if (toward == null || bodyVelocity == null || !finite(toward) || !finite(bodyVelocity)
                || toward.lengthSqr() < 1.0e-12 || !Float.isFinite(angleDegrees) || !Float.isFinite(projectileSpeed) || projectileSpeed <= 0)
            throw new IllegalArgumentException("invalid projectile velocity inputs");
        return toward.normalize().yRot((float) Math.toRadians(angleDegrees)).scale(projectileSpeed / 20d).add(bodyVelocity);
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Result rejected() { return result(Status.REJECTED, null, 0); }
    private static Result result(Status status, GunWeaponResolver.Selection selection, long nextTick) { return new Result(status, selection, nextTick); }
}
