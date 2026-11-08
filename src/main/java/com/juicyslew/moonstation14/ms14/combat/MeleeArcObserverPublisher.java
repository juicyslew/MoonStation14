package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Captures and publishes observer-only snapshots around authoritative melee resolution. */
public final class MeleeArcObserverPublisher {
    public record Selection(MeleeWeaponData weapon, ItemPresentation presentation, MeleeArcGeometry.Aim aim,
                            MeleeArcGeometry.StrikeCapture strikeGeometry, String activeHand) { }
    public static net.minecraft.resources.ResourceLocation arcForMode(MeleeWeaponData weapon, MeleeAttackRequest.Mode mode) {
        return mode == MeleeAttackRequest.Mode.WIDE ? weapon.wideArc() : weapon.arc();
    }
    private MeleeArcObserverPublisher() { }

    public static Selection capture(LivingEntity body, MeleeWeaponResolver.Selection selected, java.util.UUID targetUUID, MeleeAttackRequest.Mode mode) {
        if (body == null || selected == null) return null;
        MeleeWeaponData weapon = selected.weapon();
        ItemPresentation presentation = selected.kind() == MeleeWeaponResolver.Kind.UNARMED ? null
                : MeleeItemOwner.read(body, selected).map(view -> ItemPresentation.fromStack(view.stack())).orElse(null);
        if (selected.kind() != MeleeWeaponResolver.Kind.UNARMED && presentation == null) return null;
        Vec3 targetPosition = null;
        if (targetUUID != null && body.level() instanceof net.minecraft.server.level.ServerLevel level) {
            var target = level.getEntity(targetUUID);
            if (target != null && !target.isRemoved()) targetPosition = target.position();
        }
        var aim = MeleeArcGeometry.capture(body.position(), body.getBbHeight(), body.getLookAngle(), targetPosition, weapon.range());
        net.minecraft.world.entity.Entity nominated = targetUUID != null && body.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getEntity(targetUUID) instanceof net.minecraft.world.entity.Entity entity && !entity.isRemoved() ? entity : null;
        var strike = MeleeArcGeometry.captureStrike(body, weapon, mode, nominated, null);
        aim = MeleeArcGeometry.legacyAimWithStrikeFallback(aim, strike);
        weapon = new MeleeWeaponData(weapon.damage(), weapon.attackRate(), weapon.range(), weapon.resistanceBypass(), weapon.autoAttack(), weapon.clickDamageModifier(), weapon.mustBeEquippedToUse(), weapon.audio(), arcForMode(weapon, mode), weapon.animationOffset(), weapon.wideAnimationRotation(), weapon.bluntStaminaDamageFactor(), weapon.resetOnHandSelected(), weapon.angleDegrees(), weapon.wideArc(), weapon.altDisarm());
        return aim == null || strike == null ? null : new Selection(weapon, presentation, aim, strike, selected.activeHand());
    }

    public static Selection captureTowardValidatedTarget(LivingEntity body, MeleeWeaponResolver.Selection selected, LivingEntity target) {
        if (body == null || selected == null) return null;
        var frozen = capture(body, selected, target == null ? null : target.getUUID(), MeleeAttackRequest.Mode.LIGHT);
        if (frozen == null) return null;
        var strike = MeleeArcGeometry.captureStrikeTowardTarget(body, selected.weapon(), target);
        if (strike == null) return null;
        var aim = new MeleeArcGeometry.Aim(body.position(), strike.fullDirection(), strike.centerEndpoint());
        return new Selection(frozen.weapon(), frozen.presentation(), aim, strike, frozen.activeHand());
    }

    /** Point-directed observer capture; audience identity is intentionally not a hit nomination. */
    public static Selection captureTowardPoint(LivingEntity body, MeleeWeaponResolver.Selection selected, Vec3 point) {
        if (body == null || selected == null) return null;
        var frozen = capture(body, selected, null, MeleeAttackRequest.Mode.LIGHT);
        if (frozen == null) return null;
        var strike = MeleeArcGeometry.captureStrikeTowardPoint(body, selected.weapon(), point);
        if (strike == null) return null;
        return new Selection(frozen.weapon(), frozen.presentation(),
                new MeleeArcGeometry.Aim(body.position(), strike.fullDirection(), strike.centerEndpoint()), strike, frozen.activeHand());
    }

    public static void publish(ServerPlayer player, LivingEntity body, java.util.UUID targetUUID, Selection frozen, boolean swingLeft) {
        publish(player == null ? null : player.getUUID(), body, targetUUID, null, frozen, swingLeft);
    }

    public static void publish(ServerPlayer player, LivingEntity body, java.util.UUID targetUUID, Vec3 validatedBlockContactPoint, Selection frozen, boolean swingLeft) {
        publish(player == null ? null : player.getUUID(), body, targetUUID, validatedBlockContactPoint, frozen, swingLeft);
    }

    /** Publishes the same observer snapshot for either a player-originated or trusted server actor strike. */
    public static void publish(java.util.UUID initiatingPlayer, LivingEntity body, java.util.UUID targetUUID,
                               Vec3 validatedBlockContactPoint, Selection frozen, boolean swingLeft) {
        if (frozen == null || body == null || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var data = frozen.weapon();
        var aim = frozen.aim();
        if (validatedBlockContactPoint != null && frozen.strikeGeometry() == null) {
            var anchored = MeleeArcGeometry.capture(aim.bodyPosition(), 0, aim.direction(), validatedBlockContactPoint, data.range());
            if (anchored != null) aim = new MeleeArcGeometry.Aim(aim.bodyPosition(), anchored.direction(), validatedBlockContactPoint);
        }
        var payload = new MeleeArcPayload(body.getId(), body.getUUID(), level.dimension().location(), initiatingPlayer, data.arc(),
                data.attackRate(), data.angleDegrees(), data.animationOffset(), data.wideAnimationRotation(), aim.bodyPosition(), aim.direction(),
                  aim.attackPoint(), frozen.presentation(), swingLeft, frozen.strikeGeometry(), frozen.activeHand());
         var recipients = new java.util.LinkedHashSet<ServerPlayer>();
         var targetEntity = targetUUID == null ? null : level.getEntity(targetUUID);
          var tracked = level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(body.blockPosition()), false);
          recipients.addAll(tracked);
          for (ServerPlayer player : level.players()) {
              if (CombatPresentationAudience.controlsParticipant(player, body, targetEntity instanceof LivingEntity living ? living : null)) recipients.add(player);
              if (player.distanceToSqr(body) <= 64 * 64) recipients.add(player);
          }
          recipients.forEach(player -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload));
    }
}
