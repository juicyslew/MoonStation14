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
                         CombatMeleeTargetProbe.Result targetSnapshot, MeleeAttackEvents.MeleeHitEvent hitEvent, boolean swingLeft,
                         net.minecraft.world.phys.Vec3 validatedBlockContactPoint) {
        public Result(Status status, boolean cooldownConsumed, MeleeWeaponResolver.Selection currentSelection,
                      CombatMeleeTargetProbe.Result targetSnapshot, MeleeAttackEvents.MeleeHitEvent hitEvent, boolean swingLeft) {
            this(status, cooldownConsumed, currentSelection, targetSnapshot, hitEvent, swingLeft, null);
        }
    }
    private LiveMeleeAttackService() { }

    /** Trusted server-owned NPC strike; unlike the player adapters this has no client authority token. */
    public static boolean attackTrustedNpc(net.minecraft.world.entity.Mob attacker,
            net.minecraft.world.entity.LivingEntity victim) {
        return trustedNpcAttempt(attacker, victim, null);
    }

    /** Empty-point branch shares resolved weapon construction and publication with ordinary NPC contacts. */
    public static boolean missTrustedNpc(net.minecraft.world.entity.Mob attacker,
            net.minecraft.world.entity.LivingEntity movingTarget, net.minecraft.world.phys.Vec3 aimPoint) {
        return movingTarget != null && trustedNpcAttempt(attacker, movingTarget, aimPoint);
    }

    private static boolean trustedNpcAttempt(net.minecraft.world.entity.Mob attacker,
            net.minecraft.world.entity.LivingEntity victim, net.minecraft.world.phys.Vec3 missPoint) {
        var actor = attacker == null ? null : com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(attacker).orElse(null);
        var npc = actor == null ? null : actor.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElse(null);
        if (npc == null) return false;
        if (attacker == null || victim == null || !(attacker.level() instanceof ServerLevel level)
                || attacker.level() != victim.level() || !attacker.isAlive() || !victim.isAlive()
                || !Float.isFinite(npc.attackRate()) || npc.attackRate() <= 0 || !Float.isFinite(npc.range()) || npc.range() <= 0) return false;
        var weapon = UnarmedMeleeProfileResolver.resolve(npc);
        var audio = weapon.audio();
        var selection = new MeleeWeaponResolver.Selection(MeleeWeaponResolver.Kind.UNARMED,
                new MeleeWeaponProfile(weapon, java.util.Map.of(), Optional.empty()), attacker.getUUID(), 0, "npc", Optional.empty());
        boolean miss = missPoint != null;
        boolean positive = !miss && applyVictimDamage(level, attacker, victim, selection, java.util.Map.of(), java.util.Map.of(), false);
        var cue = miss ? audio.swing() : MeleeSoundPolicy.outcome(audio, null, positive);
        play(cue, miss ? attacker : victim, level);
        var arc = miss ? MeleeArcObserverPublisher.captureTowardPoint(attacker, selection, missPoint)
                : MeleeArcObserverPublisher.captureTowardValidatedTarget(attacker, selection, victim);
        // victim UUID here is observer audience identity only; miss capture has no entity nomination or bounds.
        MeleeArcObserverPublisher.publish((java.util.UUID)null, attacker, victim.getUUID(), null, arc, false);
        NeoForge.EVENT_BUS.post(new MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent(attacker, miss ? null : victim, cue, arc));
        return true;
    }

    public static Result attack(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                 MeleeWeaponResolver.Selection expected, UUID targetId) {
        return attack(player,authority,expected,targetId,false);
    }
    public static Result attack(ServerPlayer p, BodyActionAuthority.Snapshot a, MeleeWeaponResolver.Selection e, UUID id, boolean predicted) { return attackInternal(p,a,e,id==null?List.of():List.of(new MeleeTargetNomination.Entity(id)),false,predicted); }

    /** Authenticated LIGHT structural contact path. Block nominations never enter entity hit events. */
    public static Result attackBlock(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                     MeleeWeaponResolver.Selection expected, MeleeTargetNomination.Block nomination) {
        return attackBlock(player,authority,expected,nomination,false);
    }
    public static Result attackBlock(ServerPlayer p, BodyActionAuthority.Snapshot a, MeleeWeaponResolver.Selection e, MeleeTargetNomination.Block n, boolean predicted) { return attackInternal(p,a,e,n==null?List.of():List.of(n),false,predicted); }

    /** Server core for a client-nominated wide attack; nominations are capped before validation. */
    public static Result attackWide(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                    MeleeWeaponResolver.Selection expected, List<UUID> nominatedIds) {
        return attackWide(player, authority, expected, nominatedIds, false);
    }
    public static Result attackWide(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                    MeleeWeaponResolver.Selection expected, List<UUID> nominatedIds, boolean swingPredicted) {
        return attackInternal(player, authority, expected, nominatedIds == null ? List.of() : nominatedIds.stream().map(MeleeTargetNomination.Entity::new).map(n -> (MeleeTargetNomination)n).toList(), true, swingPredicted);
    }

    /** Typed WIDE nominations share the same admission, cap, and damage pipeline as legacy entity IDs. */
    public static Result attackWideTyped(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                         MeleeWeaponResolver.Selection expected, List<MeleeTargetNomination> nominations) {
        return attackWideTyped(player,authority,expected,nominations,false);
    }
    public static Result attackWideTyped(ServerPlayer p, BodyActionAuthority.Snapshot a, MeleeWeaponResolver.Selection e, List<MeleeTargetNomination> n, boolean predicted) { return attackInternal(p,a,e,n,true,predicted); }

    public static Result disarm(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                MeleeWeaponResolver.Selection expected, UUID targetId) {
        return disarm(player,authority,expected,targetId,false);
    }
    public static Result disarm(ServerPlayer p, BodyActionAuthority.Snapshot a, MeleeWeaponResolver.Selection e, UUID id, boolean predicted) { return attackDisarm(p,a,e,id,predicted); }

    private static Result attackDisarm(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                       MeleeWeaponResolver.Selection expected, UUID targetId, boolean swingPredicted) {
        if (player == null || authority == null || expected == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(authority.body(), expected)) return rejected();
        var body = authority.body();
        var combat = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(body)
                .flatMap(actor -> actor.component(com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent.class)).orElse(null);
        if (!MeleeSecondaryGesture.allowsDisarm(expected, combat == null ? null : combat.canDisarm().orElse(false))) return rejected();
        if (admissionReason(body, expected, targetId, level.getGameTime(), true) != MeleeAttackPolicy.Reason.ALLOWED) return rejected();
        Entity nominated = targetId == null ? null : level.getEntity(targetId);
        var pre = new MeleeAttackEvents.SharedMeleeAttackAttemptEvent(body, Optional.ofNullable(nominated), expected, true);
        NeoForge.EVENT_BUS.post(pre);
        if (pre.isCanceled()) return new Result(Status.PRE_ATTEMPT_CANCELLED, false, expected, null, null, false);
        if (!BodyActionAuthority.revalidate(player, authority) || !MeleeWeaponResolver.revalidate(body, expected)
                || !MeleeSecondaryGesture.allowsDisarm(expected, combat == null ? null : combat.canDisarm().orElse(false))
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
        var result = DisarmSystem.resolve(player, body, current, targetId, swingPredicted);
        if (result == DisarmSystem.Result.HANDLED) return new Result(Status.DISARM_HANDLED, true, current, null, null, swingLeft);
        if (result == DisarmSystem.Result.ROLLED_FAILURE) return new Result(Status.DISARM_FAILED, true, current, null, null, swingLeft);
        return new Result(Status.REJECTED, true, current, null, null, swingLeft);
    }

    private static Result attackInternal(ServerPlayer player, BodyActionAuthority.Snapshot authority,
                                   MeleeWeaponResolver.Selection expected, List<MeleeTargetNomination> typedNominations, boolean wide, boolean swingPredicted) {
        List<MeleeTargetNomination> nominations = typedNominations == null ? List.of() : typedNominations.stream().limit(5).distinct().toList();
        List<UUID> entityIds = nominations.stream().filter(MeleeTargetNomination.Entity.class::isInstance).map(MeleeTargetNomination.Entity.class::cast).map(MeleeTargetNomination.Entity::entityUUID).toList();
        MeleeTargetNomination.Block block = !wide && nominations.size() == 1 && nominations.get(0) instanceof MeleeTargetNomination.Block b ? b : null;
        UUID targetId = nominations.isEmpty() ? null : nominations.get(0) instanceof MeleeTargetNomination.Entity e ? e.entityUUID() : null;
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
          var probe = wide || block != null ? null : CombatMeleeTargetProbe.probe(level, body, targetId, current.weapon().range());
          if (!wide && block == null && probe.status() == CombatMeleeTargetProbe.Status.STALE) { play(current.weapon().audio().swing(), body, level, MeleeSoundPolicy.swingExcludedPlayer(player.getUUID(), swingPredicted)); return new Result(Status.MISS, true, current, probe, null, swingLeft); }
         Entity hit = !wide && block == null && probe.status() == CombatMeleeTargetProbe.Status.HIT ? level.getEntity(probe.targetId()) : null;
        if (hit != null && !CombatMeleeTargetProbe.revalidate(level, body, probe)) { hit = null; probe = CombatMeleeTargetProbe.probe(level, body, null, current.weapon().range()); }
           List<Entity> hits;
           var wideOutcomes = new java.util.ArrayList<WideOutcome>();
          List<StructuralContact> wideBlockContacts = new java.util.ArrayList<>();
          if (wide) {
              var collected = new java.util.ArrayList<Entity>();
              // SS14's pinned server path (Content.Server/Weapons/Melee/MeleeWeaponSystem.cs:37-54)
              // deliberately validates nominated wide targets by range/obstruction, not fan angle;
              // the client samples the fan to avoid prediction divergence from client-reported hits.
                for (UUID id : entityIds) {
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
            var blockContact = block != null && !event.handled() ? probeStructuralContact(level, body, block, current, event.bonusDamage(), wide) : null;
           boolean validBlockContact = blockContact != null;
           if (!wide && block == null && !event.handled() && hit == null) play(current.weapon().audio().swing(), body, level, MeleeSoundPolicy.swingExcludedPlayer(player.getUUID(), swingPredicted));
          MeleeAttackEvents.MeleeTargetAttackedEvent attacked = null;
          if (!wide && hit != null && !event.handled() && CombatMeleeTargetProbe.revalidate(level, body, probe)) {
             attacked = new MeleeAttackEvents.MeleeTargetAttackedEvent(body, hit, current);
            NeoForge.EVENT_BUS.post(attacked);
                boolean positiveDamage = applyVictimDamage(level, body, hit, current, event.bonusDamage(), attacked.bonusDamage(), false);
                if (!event.handled() && hit != null && !hit.isRemoved() && level.getEntity(hit.getUUID()) == hit)
                    play(MeleeSoundPolicy.outcome(current.weapon().audio(), event.hitSoundOverride().orElse(null), positiveDamage), hit, level);
          } else if (wide && !event.handled()) {
              for (Entity wideHit : hits) {
                  if (wideHit.isRemoved() || level.getEntity(wideHit.getUUID()) != wideHit) continue;
                  var targetAttacked = new MeleeAttackEvents.MeleeTargetAttackedEvent(body, wideHit, current);
                  NeoForge.EVENT_BUS.post(targetAttacked);
                   wideOutcomes.add(new WideOutcome(wideHit, applyVictimDamage(level, body, wideHit, current, event.bonusDamage(), targetAttacked.bonusDamage(), true)));
              }
              for (var nomination : nominations) if (nomination instanceof MeleeTargetNomination.Block blockContactNomination) {
                   var contact = probeStructuralContact(level, body, blockContactNomination, current, event.bonusDamage(), true);
                  if (contact != null) wideBlockContacts.add(contact);
              }
          }
            if (block != null && !event.handled() && validBlockContact) {
                playAt(MeleeSoundPolicy.outcome(current.weapon().audio(), event.hitSoundOverride().orElse(null), false), blockContact.location(), level, null);
            } else if (block != null && !event.handled()) {
                 play(current.weapon().audio().swing(), body, level, MeleeSoundPolicy.swingExcludedPlayer(player.getUUID(), swingPredicted));
            }
            if (wide && !event.handled()) {
                wideOutcomes.removeIf(outcome -> outcome.entity().isRemoved() || level.getEntity(outcome.entity().getUUID()) != outcome.entity());
                var survivingBlocks = wideBlockContacts.stream().filter(contact -> contact.survives(level)).toList();
                var choices = new java.util.ArrayList<WideOutcome>(wideOutcomes);
                choices.addAll(survivingBlocks.stream().map(contact -> new WideOutcome(null, false, contact)).toList());
                int cueIndex = MeleeSoundPolicy.wideContactIndex(choices.stream().map(WideOutcome::positive).toList());
                boolean hasPositive = cueIndex >= 0 && choices.get(cueIndex).positive();
                WideOutcome selected = cueIndex < 0 ? null : choices.get(cueIndex);
                Entity firstContact = selected == null ? null : selected.entity();
                 boolean anyContact = firstContact != null || !survivingBlocks.isEmpty();
                if (MeleeSoundPolicy.shouldPlayHit(false, anyContact)) {
                     var cue = MeleeSoundPolicy.outcome(current.weapon().audio(), event.hitSoundOverride().orElse(null), hasPositive);
                     if (firstContact != null) play(cue, firstContact, level);
                      else playAt(cue, selected.block().location(), level, null);
                }
          } else if (hit != null && !event.handled() && MeleeSoundPolicy.shouldPlayHit(false, !hit.isRemoved() && level.getEntity(hit.getUUID()) == hit)) {
              // Single-target hit cue was selected from the committed damage outcome above.
         }
            if (wide && MeleeSoundPolicy.shouldPredictSwing(hits.isEmpty() && wideBlockContacts.isEmpty()) && !event.handled())
                 play(current.weapon().audio().swing(), body, level, MeleeSoundPolicy.swingExcludedPlayer(player.getUUID(), swingPredicted));
             return new Result(event.handled() ? Status.HANDLED : (wide ? !hits.isEmpty() || !wideBlockContacts.isEmpty() : block != null ? validBlockContact : hit != null) ? Status.HIT : Status.MISS, true, current, probe, event, swingLeft,
                     !event.handled() && wide && hits.isEmpty() && !wideBlockContacts.isEmpty() ? wideBlockContacts.get(0).location()
                             : !event.handled() && block != null && validBlockContact ? blockContact.location() : null);
     }
      private record WideOutcome(Entity entity, boolean positive, StructuralContact block) {
          WideOutcome(Entity entity, boolean positive) { this(entity, positive, null); }
      }
      private record StructuralContact(net.minecraft.world.phys.Vec3 location) {
          boolean survives(ServerLevel level) { return true; }
      }
      private static StructuralContact probeStructuralContact(ServerLevel level, net.minecraft.world.entity.LivingEntity body,
              MeleeTargetNomination.Block nomination, MeleeWeaponResolver.Selection selection, java.util.Map<String, Float> bonus, boolean wide) {
          var target = com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe.probeNominatedContact(
                  level, body, nomination.pos(), nomination.face(), nomination.hitpoint(), selection.weapon().range() + (wide ? .1 : 0));
         if (target == null) return null;
         var state = level.getBlockState(target.pos());
         if (net.minecraft.world.level.block.Block.getId(state) != nomination.expectedBlockStateID()) return null;
          var block = state.getBlock();
           var adjusted = MeleeDamageComposition.compose(selection.weapon().damage(), baseScale(wide, selection.weapon().clickDamageModifier()), bonus, java.util.Map.of());
           if (com.juicyslew.moonstation14.ms14.combat.StructuralContactResolver.evaluate(block, adjusted,
                   com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverConstructionEntities(),
                   com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverBlockStructuralContacts())
                   != com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT) return null;
         return new StructuralContact(target.location());
     }
    private static boolean applyVictimDamage(ServerLevel level, net.minecraft.world.entity.LivingEntity body, Entity victim,
            MeleeWeaponResolver.Selection selection, java.util.Map<String, Float> hitBonus,
            java.util.Map<String, Float> targetBonus, boolean wide) {
        var data = selection.weapon();
        var damage = MeleeDamageComposition.compose(data.damage(), baseScale(wide, data.clickDamageModifier()), hitBonus, targetBonus);
         if (damage.isEmpty() || victim.isRemoved() || level.getEntity(victim.getUUID()) != victim) return false;
        if (victim instanceof net.minecraft.world.entity.LivingEntity living) {
             var committed = DamageSystem.applyHealthChangeDetailed(living, damage, 1f, data.resistanceBypass(), DamageSystem.Cause.COMBAT, body);
             float stamina = MeleeStaminaPolicy.amount(committed.committedPositive().getOrDefault(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 0f), data.bluntStaminaDamageFactor());
             if (stamina > 0f) com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.takeStaminaDamage(living, stamina);
             return !committed.committedPositive().isEmpty();
         } else if (victim instanceof LooseItemEntity loose
                 && loose.applyTypedItemDamage(level, damage, data.resistanceBypass()) && !loose.isRemoved()
                 && level.getEntity(loose.getUUID()) == loose)
              com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload.publish(loose,
                      com.juicyslew.moonstation14.ms14.damage.DamageFeedbackProfile.PHYSICAL, 6);
         return victim instanceof LooseItemEntity loose && !loose.isRemoved() && level.getEntity(loose.getUUID()) == loose;
    }
    static List<UUID> wideNominationPolicy(List<UUID> ids) { return ids == null ? List.of() : ids.stream().limit(5).distinct().toList(); }
    static float baseScale(boolean wide, float clickModifier) { return wide ? 1f : clickModifier; }
     private static void play(PositionalSoundCue cue, Entity at, ServerLevel level) { play(cue, at, level, null); }
    private static void play(PositionalSoundCue cue, Entity at, ServerLevel level, UUID excludedPlayer) {
        if (cue == null) return;
         float pitch = 1f + (level.getRandom().nextFloat() * 2f - 1f) * cue.pitchVariation();
         PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, at.getX(), at.getY(), at.getZ(), cue.volume(), pitch, excludedPlayer);
     }
     private static void playAt(PositionalSoundCue cue, net.minecraft.world.phys.Vec3 at, ServerLevel level, UUID excludedPlayer) {
         if (cue == null) return;
         float pitch = 1f + (level.getRandom().nextFloat() * 2f - 1f) * cue.pitchVariation();
         PositionalSoundRouter.play(level, cue.event(), SoundSource.PLAYERS, at.x, at.y, at.z, cue.volume(), pitch, excludedPlayer);
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
