package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.mobs.MobStateSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.audio.PositionalSoundRouter;
import net.minecraft.sounds.SoundSource;

/** Trusted server-thread melee admission and ordinary typed damage pipeline. */
public final class LiveMeleeAttackService {
    public enum Status { REJECTED, PRE_ATTEMPT_CANCELLED, CONSUMED_CANCELLED, MISS, HIT, HANDLED, DISARM_FAILED, DISARM_HANDLED }
    public record Result(Status status, boolean cooldownConsumed, MeleeWeaponResolver.Selection currentSelection,
                         CombatMeleeTargetProbe.Result targetSnapshot, MeleeAttackEvents.MeleeHitEvent hitEvent, boolean swingLeft) { }
    private LiveMeleeAttackService() { }

    public static Result attack(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                 MeleeWeaponResolver.Selection expected, UUID targetId) {
        return attackInternal(player, authority, expected, targetId == null ? List.of() : List.of(targetId), false);
    }

    /** Server core for a client-nominated wide attack; nominations are capped before validation. */
    public static Result attackWide(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                    MeleeWeaponResolver.Selection expected, List<UUID> nominatedIds) {
        return attackInternal(player, authority, expected, nominatedIds, true);
    }

    public static Result disarm(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                MeleeWeaponResolver.Selection expected, UUID targetId) {
        return attackDisarm(player, authority, expected, targetId);
    }

    private static Result attackDisarm(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                      MeleeWeaponResolver.Selection expected, UUID targetId) {
        if (player == null || authority == null || expected == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(authority.body(), expected)) return rejected();
        var body = authority.body();
        if (admissionReason(body, expected, targetId, level.getGameTime(), true) != MeleeAttackPolicy.Reason.ALLOWED) return rejected();
        Entity nominated = targetId == null ? null : level.getEntity(targetId);
        var pre = new MeleeAttackEvents.SharedMeleeAttackAttemptEvent(body, Optional.ofNullable(nominated), expected, true);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return new Result(Status.PRE_ATTEMPT_CANCELLED, false, expected, null, null, false);
        if (!BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(body, expected)
                || admissionReason(body, expected, targetId, level.getGameTime(), true) != MeleeAttackPolicy.Reason.ALLOWED) return rejected();
        var consumed = MeleeCooldownSystem.consume(body, expected, level.getGameTime()).orElse(null);
        if (consumed == null) return rejected();
        var current = consumed.selection();
        var after = new MeleeAttackEvents.AttemptMeleeEvent(body, Optional.ofNullable(nominated), current);
        NeoForge.EVENT_BUS.post(after);
        var finished = MeleeCooldownSystem.finishAttempt(body, current).orElse(null);
        if (finished != null) current = finished.selection();
        boolean swingLeft = finished != null && finished.swingLeft();
        if (after.isCanceled()) return new Result(Status.CONSUMED_CANCELLED, true, current, null, null, swingLeft);
        var result = DisarmSystem.resolve(player, body, current, targetId);
        if (result == DisarmSystem.Result.HANDLED) return new Result(Status.DISARM_HANDLED, true, current, null, null, swingLeft);
        if (result == DisarmSystem.Result.ROLLED_FAILURE) return new Result(Status.DISARM_FAILED, true, current, null, null, swingLeft);
        return new Result(Status.REJECTED, true, current, null, null, swingLeft);
    }

    private static Result attackInternal(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                 MeleeWeaponResolver.Selection expected, List<UUID> nominatedIds, boolean wide) {
        List<UUID> nominations = wideNominationPolicy(nominatedIds);
        UUID targetId = nominations.isEmpty() ? null : nominations.get(0);
        UUID admissionTarget = wide ? null : targetId;
        if (player == null || authority == null || expected == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(authority.body(), expected)) return rejected();
        var body = authority.body();
        if (admissionReason(body, expected, admissionTarget, level.getGameTime()) != MeleeAttackPolicy.Reason.ALLOWED) return rejected();
        Entity nominated = !wide && targetId != null ? level.getEntity(targetId) : null;
        var pre = new MeleeAttackEvents.SharedMeleeAttackAttemptEvent(body, Optional.ofNullable(nominated), expected, false);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return new Result(Status.PRE_ATTEMPT_CANCELLED, false, expected, null, null, false);
        if (!BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(body, expected)
                || admissionReason(body, expected, admissionTarget, level.getGameTime()) != MeleeAttackPolicy.Reason.ALLOWED) return rejected();
        var consumed = MeleeCooldownSystem.consume(body, expected, level.getGameTime()).orElse(null);
        if (consumed == null) return rejected();
        var current = consumed.selection();
        var after = new MeleeAttackEvents.AttemptMeleeEvent(body, Optional.ofNullable(nominated), current);
        NeoForge.EVENT_BUS.post(after);
        var finished = MeleeCooldownSystem.finishAttempt(body, current).orElse(null);
        if (finished != null) current = finished.selection();
        boolean swingLeft = finished != null && finished.swingLeft();
        if (after.isCanceled()) return new Result(Status.CONSUMED_CANCELLED, true, current, null, null, swingLeft);
         var probe = wide ? null : CombatMeleeTargetProbe.probe(level, body, targetId, current.weapon().range());
         if (!wide && probe.status() == CombatMeleeTargetProbe.Status.STALE) { play(current.weapon().audio().swing(), body, level, targetId == null ? player.getUUID() : null); return new Result(Status.MISS, true, current, probe, null, swingLeft); }
         Entity hit = !wide && probe.status() == CombatMeleeTargetProbe.Status.HIT ? level.getEntity(probe.targetId()) : null;
        if (hit != null && !CombatMeleeTargetProbe.revalidate(level, body, probe)) { hit = null; probe = CombatMeleeTargetProbe.probe(level, body, null, current.weapon().range()); }
         List<Entity> hits;
         if (wide) {
             var collected = new java.util.ArrayList<Entity>();
             for (UUID id : nominations) {
                 if (id == null) continue;
                 var candidate = CombatMeleeTargetProbe.probe(level, body, id, current.weapon().range() + .1f);
                 Entity entity = candidate.status() == CombatMeleeTargetProbe.Status.HIT ? level.getEntity(id) : null;
                  if (entity != null && CombatMeleeTargetProbe.revalidate(level, body, candidate)
                          && PacificationSystem.mayMeleeTarget(body, entity, current.weapon().damage())
                          && !collected.contains(entity)) collected.add(entity);
             }
             hits = List.copyOf(collected);
         } else hits = hit == null ? List.of() : List.of(hit);
         var event = new MeleeAttackEvents.MeleeHitEvent(body, hits, current);
        NeoForge.EVENT_BUS.post(event);
          if (!wide && !event.handled() && hit == null) play(current.weapon().audio().swing(), body, level, targetId == null ? player.getUUID() : null);
          MeleeAttackEvents.MeleeTargetAttackedEvent attacked = null;
          if (!wide && hit != null && !event.handled() && CombatMeleeTargetProbe.revalidate(level, body, probe)) {
             attacked = new MeleeAttackEvents.MeleeTargetAttackedEvent(body, hit, current);
            NeoForge.EVENT_BUS.post(attacked);
              applyVictimDamage(level, body, hit, current, event.bonusDamage(), attacked.bonusDamage(), false);
          } else if (wide && !event.handled()) {
              for (Entity wideHit : hits) {
                  if (wideHit.isRemoved() || level.getEntity(wideHit.getUUID()) != wideHit) continue;
                  var targetAttacked = new MeleeAttackEvents.MeleeTargetAttackedEvent(body, wideHit, current);
                  NeoForge.EVENT_BUS.post(targetAttacked);
                  applyVictimDamage(level, body, wideHit, current, event.bonusDamage(), targetAttacked.bonusDamage(), true);
              }
          }
          if (wide && !event.handled()) {
              Entity firstNominated = nominations.isEmpty() || nominations.get(0) == null ? null : level.getEntity(nominations.get(0));
              if (playWideHitSound(firstNominated != null && !firstNominated.isRemoved() && level.getEntity(firstNominated.getUUID()) == firstNominated))
                  play(MeleeSoundPolicy.hit(current.weapon().audio(), event.hitSoundOverride().orElse(null)), firstNominated, level);
          } else if (hit != null && !event.handled() && MeleeSoundPolicy.shouldPlayHit(false, !hit.isRemoved() && level.getEntity(hit.getUUID()) == hit)) {
             var cue = MeleeSoundPolicy.hit(current.weapon().audio(), event.hitSoundOverride().orElse(null));
             play(cue, hit, level);
         }
          if (wide && shouldSwingWide(nominations.isEmpty(), event.handled())) play(current.weapon().audio().swing(), body, level, player.getUUID());
          return new Result(event.handled() ? Status.HANDLED : (wide ? !hits.isEmpty() : hit != null) ? Status.HIT : Status.MISS, true, current, probe, event, swingLeft);
    }
    private static void applyVictimDamage(ServerLevel level, net.minecraft.world.entity.LivingEntity body, Entity victim,
            MeleeWeaponResolver.Selection selection, java.util.Map<String, Float> hitBonus,
            java.util.Map<String, Float> targetBonus, boolean wide) {
        var data = selection.weapon();
        var damage = MeleeDamageComposition.compose(data.damage(), baseScale(wide, data.clickDamageModifier()), hitBonus, targetBonus);
        if (damage.isEmpty() || victim.isRemoved() || level.getEntity(victim.getUUID()) != victim) return;
        if (victim instanceof net.minecraft.world.entity.LivingEntity living) {
            var committed = DamageSystem.applyHealthChangeDetailed(living, damage, 1f, data.resistanceBypass(), DamageSystem.Cause.COMBAT, body);
            float stamina = MeleeStaminaPolicy.amount(committed.committedPositive().getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 0f), data.bluntStaminaDamageFactor());
            if (stamina > 0f) com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.takeStaminaDamage(living, stamina);
        } else if (victim instanceof LooseItemEntity loose) loose.applyTypedItemDamage(level, damage, data.resistanceBypass());
    }
    static List<UUID> wideNominationPolicy(List<UUID> ids) { return ids == null ? List.of() : ids.stream().limit(5).distinct().toList(); }
    static float baseScale(boolean wide, float clickModifier) { return wide ? 1f : clickModifier; }
    static boolean shouldSwingWide(boolean initiallyEmpty, boolean handled) { return initiallyEmpty && !handled; }
    static boolean playWideHitSound(boolean firstNominatedStillPresent) { return firstNominatedStillPresent; }
     private static void play(PositionalSoundCue cue, Entity at, ServerLevel level) { play(cue, at, level, null); }
      private static void play(PositionalSoundCue cue, Entity at, ServerLevel level, UUID excludedPlayer) {
        if (cue == null) return;
         float pitch = 1f + (level.getRandom().nextFloat() * 2f - 1f) * cue.pitchVariation();
         PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, at.getX(), at.getY(), at.getZ(), cue.volume(), pitch, excludedPlayer);
    }
    private static MeleeAttackPolicy.Reason admissionReason(net.minecraft.world.entity.LivingEntity body,
            MeleeWeaponResolver.Selection expected, UUID targetId, long now) { return admissionReason(body, expected, targetId, now, false); }
    private static MeleeAttackPolicy.Reason admissionReason(net.minecraft.world.entity.LivingEntity body,
            MeleeWeaponResolver.Selection expected, UUID targetId, long now, boolean disarm) {
        var deadline = MeleeCooldownSystem.nextAttackTick(body, expected);
        boolean sourceMatch = MeleeWeaponResolver.revalidate(body, expected) && deadline.isPresent();
        boolean ready = sourceMatch && now >= deadline.getAsLong();
        return MeleeAttackPolicy.evaluate(CombatModeSystem.isInCombatMode(body), CharacterControlSystem.isStunned(body),
                MobStateSystem.isCritical(body), MobStateSystem.isDead(body), sourceMatch, !ready,
                !disarm && expected.kind() == MeleeWeaponResolver.Kind.UNARMED && body.getUUID().equals(targetId));
    }
    private static Result rejected() { return new Result(Status.REJECTED, false, null, null, null, false); }
}
