package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess;
import com.juicyslew.moonstation14.ms14.hands.live.LiveWieldSystem;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
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
        return fire(player,authority,expected,nominatedAimPoint,0);
    }
    public static Result fire(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                              GunWeaponResolver.Selection expected, Vec3 nominatedAimPoint, long shotSequence) {
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
        var visualSource = new ShotVisualSource(expected.activeHand(), BuiltInRegistries.ITEM.getKey(owned.stack().getItem()), shotSequence);
        var eventGun = new GunAttackEvents.OwnedGunBinding(binding, expected);
        if (!GunAttackEvents.dispatchShotAttempted(body, eventGun, NeoForge.EVENT_BUS::post)) return rejected();
        if (!BodyActionAuthority.revalidate(player, authority) || !CharacterControlSystem.canAct(body)
                || !CombatModeSystem.isInCombatMode(body) || !GunWeaponResolver.revalidate(body, expected)
                || LiveOwnedItemAccess.read(body, binding).isEmpty()) return rejected();
        ItemStack staged = GunItemFactory.initializeCopy(owned.stack());
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
        boolean wielded = LiveWieldSystem.isWielded(body, expected.activeHand(), expected.itemToken());
        if (!GunModePolicy.mayFire(gun, wielded)) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
        String effectiveMode = GunModePolicy.effectiveFireMode(runtime, gun);
        GunAmmoProviderKernel.TakeResult plan = gun.batteryAmmoProvider().isPresent()
                ? BatteryAmmoProviderKernel.take(staged, gun, ModBatteryProfiles.serverCatalog().asMap(),
                    ModProjectileProfiles.serverCatalog().asMap(), GunModePolicy.selectedBatteryMode(runtime, gun))
                : GunAmmoProviderKernel.takeOne(staged, gun, ModCartridgeProfiles.serverCatalog().asMap(), ModProjectileProfiles.serverCatalog().asMap());
        if (plan.status() == GunAmmoProviderKernel.Status.INVALID)
            return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
        ItemStack committedGun = plan.gun();
        MagazineChamberState beforeMagazineState = staged.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
        GunShotEmitter.PreparedShot prepared = null;
        float recoilAngle = 0f;
        if (plan.status() == GunAmmoProviderKernel.Status.EMPTY) {
            var safeDeadline = GunFirePolicy.emptyDeadline(now, deadline, gun.gun().burstCooldownSeconds());
            if (safeDeadline.isEmpty()) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
            long safe = safeDeadline.getAsLong();
            nextRuntime = runtime.withFire(safe, runtime.lastFireTick(), runtime.currentSpreadDegrees());
        } else if (plan.status() == GunAmmoProviderKernel.Status.READY || plan.status() == GunAmmoProviderKernel.Status.DRY_FIRE) {
            var spreadData = GunModePolicy.effectiveSpread(gun, wielded);
            var spread = GunFirePolicy.recoil(runtime, spreadData, now, level.getRandom().nextFloat() * 2f - 1f);
            nextRuntime = runtime.withFire(deadline, deadline, spread.currentSpreadDegrees());
            recoilAngle = spread.angleDegrees();
            if (plan.status() == GunAmmoProviderKernel.Status.READY) {
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(eye))) return rejected();
            prepared = GunShotEmitter.prepare(level, body, plan.projectile(), eye,
                    RangedDirection.applySpread(toward, Math.toRadians(recoilAngle)), body.getDeltaMovement(),
                    gun.gun().projectileSpeed(), gun.gunSpreadModifier()).orElse(null);
            if (prepared == null) return result(Status.INVALID_PROFILE, expected, runtime.nextFireTick());
            }
        }

        committedGun.set(ModDataComponents.GUN_RUNTIME_STATE.get(), nextRuntime);
        if (!commit(player, authority, body, expected, owned, committedGun)) return rejected();
        var refreshed = GunWeaponResolver.resolve(body).orElse(null);
        if (plan.status() == GunAmmoProviderKernel.Status.DRY_FIRE) playEmptyCue(level, body, player, gun);
        var afterMagazineState = committedGun.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
        if (plan.status() != GunAmmoProviderKernel.Status.EMPTY && gun.magazineChamberProvider().isPresent()
                && beforeMagazineState != null && beforeMagazineState.boltClosed()
                && afterMagazineState != null && !afterMagazineState.boltClosed())
            gun.magazineChamberProvider().orElseThrow().boltOpenSound().ifPresent(cue -> PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS,
                    body.getX(), body.getY(), body.getZ(), cue.volume(), MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), cue.pitchVariation()), null));
        if (plan.status() == GunAmmoProviderKernel.Status.EMPTY) {
            var emptyEvent = new GunAttackEvents.EmptyGunShotEvent(body, eventGun);
            NeoForge.EVENT_BUS.post(emptyEvent);
            playEmptyCue(level, body, player, gun);
            return result(Status.EMPTY, refreshed, nextRuntime.nextFireTick());
        }
        try {
            if (plan.ejectedRound().isPresent()) {
                var sourceSelection = GunWeaponResolver.resolve(body).orElse(null);
                var sourceView = sourceSelection == null ? null : GunWeaponResolver.readOwned(body, sourceSelection).orElse(null);
                if (sourceSelection == null || sourceView == null || !sourceView.stack().is(plan.gun().getItem())
                        || !ItemStack.isSameItemSameComponents(sourceView.stack(), committedGun))
                    return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
                var output = plan.ejectedRound().orElseThrow();
                var published = CartridgeOutputPublisher.publish(level,player,body,body.position(),toward.normalize(),
                        expected.activeHand().toLowerCase(java.util.Locale.ROOT).contains("left"),java.util.List.of(output),
                        () -> BodyActionAuthority.revalidate(player, authority) && GunWeaponResolver.revalidate(body, sourceSelection)
                                && LiveOwnedItemAccess.read(body, new LiveOwnedItemAccess.Binding(LiveOwnedItemAccess.Location.HELD,
                                        sourceSelection.activeHand(), sourceSelection.handRevision(), sourceSelection.itemToken()))
                                        .map(v -> ItemStack.isSameItemSameComponents(v.stack(), committedGun)).orElse(false));
                if (published != BodyHandItemTransfer.Result.SUCCESS)
                    return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
            }
            if (plan.status() == GunAmmoProviderKernel.Status.DRY_FIRE) {
                NeoForge.EVENT_BUS.post(new GunAttackEvents.GunShotEvent(body, eventGun));
                return result(Status.EMPTY, refreshed, nextRuntime.nextFireTick());
            }
            if (!prepared.publish(visualSource)) return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
        } catch (RuntimeException denied) {
            return result(Status.CONSUMED_PUBLICATION_FAILED, refreshed, nextRuntime.nextFireTick());
        }
        var shotProfile=ModProjectileProfiles.serverCatalog().get(plan.projectile());
        var flash=shotProfile == null ? null : shotProfile.muzzleFlash();
        GunMuzzleFlashObserverPublisher.publish(player,body,flash,eye,toward.normalize(),visualSource);
        gun.gun().gunshotSound().ifPresent(cue -> PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS,
                body.getX(), body.getY(), body.getZ(), cue.volume(), MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), cue.pitchVariation()), player.getUUID()));
        var shotEvent = new GunAttackEvents.GunShotEvent(body, eventGun);
        NeoForge.EVENT_BUS.post(shotEvent);
        return result(Status.FIRED, refreshed, nextRuntime.nextFireTick());
    }

    private static void playEmptyCue(ServerLevel level, net.minecraft.world.entity.LivingEntity body, ServerPlayer player, GunProfile gun) {
        gun.gun().emptySound().ifPresent(cue -> PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS,
                body.getX(), body.getY(), body.getZ(), cue.volume(), MeleeSoundPolicy.pitch(1f, level.getRandom().nextFloat(), cue.pitchVariation()), player.getUUID()));
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
        return RangedDirection.applySpread(toward, Math.toRadians(angleDegrees)).scale(projectileSpeed / 20d).add(bodyVelocity);
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Result rejected() { return result(Status.REJECTED, null, 0); }
    private static Result result(Status status, GunWeaponResolver.Selection selection, long nextTick) { return new Result(status, selection, nextTick); }
}
