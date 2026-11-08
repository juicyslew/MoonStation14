package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.live.BodyLooseThrow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.LinkedHashSet;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;

/** Publishes only committed hand releases to involved players and nearby observers. */
public final class DisarmFlightNetworking {
 private DisarmFlightNetworking() { }
 public static void publish(LivingEntity actor, LivingEntity victim, BodyLooseThrow.CommittedFlight flight) {
  if(actor==null||victim==null||flight==null||!(victim.level() instanceof net.minecraft.server.level.ServerLevel level)||actor.level()!=level)return;
  var recipients=new LinkedHashSet<ServerPlayer>();
  if(actor instanceof ServerPlayer p)recipients.add(p);
  if(victim instanceof ServerPlayer p)recipients.add(p);
  // Passive cue audience must resolve the owners of controlled bodies without invoking
  // HandActorAuthority (whose input/menu admission gates are intentionally irrelevant here).
   for (ServerPlayer p : level.players()) if (CombatPresentationAudience.controlsParticipant(p, actor, victim)) recipients.add(p);
  for(ServerPlayer p:level.players()) if(include(p.getUUID(), null, actor.getUUID(), victim.getUUID(), p.position().distanceToSqr(flight.origin())))recipients.add(p);
    recipients.forEach(p->PacketDistributor.sendToPlayer(p,new TransientDisarmFlightPayload(level.dimension().location(),flight.entityId(),flight.origin(),flight.velocity(), participant(p, actor, victim), controlsActor(p, actor))));
   net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new MeleeAttackEvents.DisarmFlightPublishedEvent(
           actor.getUUID(), victim.getUUID(), flight, recipients.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toUnmodifiableSet()),
           level.dimension(), level.getGameTime()));
 }
 static boolean include(java.util.UUID player, java.util.UUID controlledBody, java.util.UUID actor, java.util.UUID victim, double distanceSquared) {
  return player.equals(actor) || player.equals(victim) || actor.equals(controlledBody) || victim.equals(controlledBody) || distanceSquared <= 64 * 64;
 }
 static boolean participant(ServerPlayer player, LivingEntity actor, LivingEntity victim) {
  return player == actor || player == victim || CombatPresentationAudience.controlsParticipant(player, actor, victim);
 }
 static boolean controlsActor(ServerPlayer player, LivingEntity actor) {
  return actorRole(player == actor, CombatPresentationAudience.controlsParticipant(player, actor, null));
 }
 static boolean actorRole(boolean directActor, boolean controlsActorBody) { return directActor || controlsActorBody; }
}
