package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/** Opt-in bridge for a real connected client's melee presentation capture. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class MeleePresentationServerDiagnostic {
    private static final Set<String> PHASES = Set.of("fist_first", "fist_third", "disarm_first", "fist_hit_near", "fist_hit_edge", "wall_first", "knife_first", "observer_setup", "observer_swing");
     private static final Set<String> MOTION_OBSERVER_SCENES = Set.of("motion_observer_up", "motion_observer_down", "motion_observer_close", "motion_observer_wide", "motion_observer_disarm_flight");
    private static final Set<String> STRIKE_SCENES = Set.of("motion_fist_first", "motion_fist_third", "motion_knife_first", "motion_knife_wide", "motion_wall_clip", "motion_disarm_first", "motion_crowbar_light", "motion_jug_hit", "motion_knife_repeat", "motion_knife_wide_up", "motion_knife_wide_vertical_up", "motion_knife_wide_down", "motion_knife_wide_vertical_down", "motion_crowbar_wall_wide", "motion_disarm_flight", "motion_observer_disarm_flight");
     private static com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmFlightPublishedEvent lastFlightReceipt;
     private record FlightExpectation(java.util.UUID actor, java.util.UUID victim, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> world, long setupTick, String scene, Set<java.util.UUID> recipients) { }
      private static FlightExpectation flightExpectation;
      private static long fixtureChanceSeed = -1L;
    private static Path currentScenePath;
    public static JsonObject writeFlightSceneIdentity(JsonObject json, boolean observer, int primaryId, String primaryUUID, long primaryEpoch, int peerId, String peerUUID, long peerEpoch) {
        json.addProperty("primaryBodyId", primaryId); json.addProperty("primaryBodyUUID", primaryUUID); json.addProperty("primaryEpoch", primaryEpoch);
        json.addProperty("peerBodyId", peerId); json.addProperty("peerBodyUUID", peerUUID); json.addProperty("peerEpoch", peerEpoch);
        String actor = observer ? peerUUID : primaryUUID, victim = observer ? primaryUUID : peerUUID;
        json.addProperty("targetId", observer ? primaryId : peerId); json.addProperty("targetUUID", victim);
        json.addProperty("expectedActorUUID", actor); json.addProperty("expectedVictimUUID", victim);
        return json;
    }
     @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
     public static void seedFixtureDisarmChance(com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmAttemptEvent event) {
         var expected = flightExpectation;
         if (!enabled() || expected == null || event.isCanceled()
                 || event.disarmSource() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmSource.TARGET
                 || !event.source().getUUID().equals(expected.actor()) || !event.target().getUUID().equals(expected.victim())
                 || !(event.target().level() instanceof net.minecraft.server.level.ServerLevel level)
                 || !level.dimension().equals(expected.world())) return;
         var policy = com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy.resolveActor(event.target());
         var stamina = policy.flatMap(c -> c.component(com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent.class)).orElse(null);
         if (stamina == null) return;
         float failure = 1f - event.pushDamage() / stamina.baseCritThreshold();
         var weapon = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(event.source()).orElse(null);
          boolean swing = weapon != null && weapon.weapon().audio().swing() != null;
         for (long seed = 0; seed < 1024; seed++) {
             var candidate = net.minecraft.util.RandomSource.create(seed);
             if (swing) candidate.nextFloat();
             if (candidate.nextFloat() >= failure) {
                 level.getRandom().setSeed(seed);
                 fixtureChanceSeed = seed;
                 return;
             }
         }
         fixtureChanceSeed = -1L;
         terminalFailure(expected.scene(), new IllegalStateException("no deterministic fixture seed accepted disarm chance within 0..1023"));
     }
     @SubscribeEvent public static void observeFlight(com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmFlightPublishedEvent event) {
         if (!enabled()) return;
         lastFlightReceipt = event;
         var expected = flightExpectation;
          if (expected == null || !event.actorUUID().equals(expected.actor()) || !event.victimUUID().equals(expected.victim())
                  || !event.world().equals(expected.world()) || event.publishedTick() < expected.setupTick()) return;
          if (!event.recipientUUIDs().containsAll(expected.recipients())) {
              terminalFailure(expected.scene(), new IllegalStateException("committed flight audience missing expected recipients: expected=" + expected.recipients() + " actual=" + event.recipientUUIDs()));
              return;
          }
         try {
             if (currentScenePath == null || !Files.exists(currentScenePath)) return;
             JsonObject scene = com.google.gson.JsonParser.parseString(Files.readString(currentScenePath, StandardCharsets.UTF_8)).getAsJsonObject();
             if (!expected.scene().equals(baseScene(scene.get("scene").getAsString()))) return;
             writeFlight(scene, event);
             scene.addProperty("releaseReady", true);
             atomicSceneWrite(currentScenePath, scene);
         } catch (Exception failure) { System.err.println("MS14_MELEE_CAPTURE_FLIGHT_REFRESH_FAILED error=" + failure); }
    }
    private static RegisteredServerPlayerFixture peer;
    private static RegisteredPlayerReadyDriver.Active peerActive;
    private static com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply swingReply;
    private static int swingSequence;
    private static com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin peerBegin;
    private static java.util.UUID pendingOwner;
    private static String pendingPhase;
    private static long pendingDeadline;
    private static long diagnosticTicks;
    private static com.juicyslew.moonstation14.entities.LooseItemEntity motionJugTarget;
    private static boolean disarmScene(String phase) { return phase.equals("disarm_first") || phase.equals("motion_disarm_first"); }
    private MeleePresentationServerDiagnostic() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        if (!enabled()) return;
        event.getDispatcher().register(Commands.literal("ms14_melee_capture")
                .then(Commands.argument("phase", StringArgumentType.word())
                        .suggests((ctx, builder) -> { PHASES.forEach(builder::suggest); STRIKE_SCENES.forEach(builder::suggest); MOTION_OBSERVER_SCENES.forEach(builder::suggest); MOTION_OBSERVER_SCENES.stream().map(s -> s + "_swing").forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(ctx -> capture(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "phase")))));
    }

    @SubscribeEvent
    public static void serverStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (peer != null) { peer.close(); peer = null; peerActive = null; }
        swingReply = null;
        clearPending();
    }

    @SubscribeEvent
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!enabled()) return;
        diagnosticTicks++;
        if (pendingOwner == null) return;
        if (event.getServer().getPlayerList().getPlayer(pendingOwner) == null) {
            clearPending();
            if (peer != null) { peer.close(); peer = null; peerActive = null; swingReply = null; }
            return;
        }
        if (peerBegin != null && peer != null && peer.isRegisteredAndAlive()) {
            try {
                peerActive = RegisteredPlayerReadyDriver.readyAndQuery(peer, peerBegin);
                peerBegin = null;
                var owner = event.getServer().getPlayerList().getPlayer(pendingOwner);
                String phase = pendingPhase;
                clearPending();
                if (owner != null) capture(owner, phase);
            } catch (Exception failure) { terminalFailure(pendingPhase, failure); }
        } else if (diagnosticTicks >= pendingDeadline) {
            terminalFailure(pendingPhase, new IllegalStateException("timed out waiting for real CHARACTER Begin"));
        }
    }

    private static boolean enabled() {
        return (Boolean.getBoolean("ms14.meleePresentationDiagnostic") || Boolean.getBoolean("ms14.meleeStrikePresentationDiagnostic"))
                && System.getProperty("ms14.meleePresentationCaptureDir") != null;
    }

    private static int capture(ServerPlayer player, String phase) {
        boolean motionObserverSetup = MOTION_OBSERVER_SCENES.contains(phase);
        boolean motionObserverSwing = phase.endsWith("_swing") && MOTION_OBSERVER_SCENES.contains(phase.substring(0, phase.length() - 6));
        String motionScene = motionObserverSwing ? phase.substring(0, phase.length() - 6) : phase;
        if (!enabled() || !(PHASES.contains(phase) || (Boolean.getBoolean("ms14.meleeStrikePresentationDiagnostic") && (STRIKE_SCENES.contains(phase) || motionObserverSetup || motionObserverSwing))) || player instanceof FakePlayer
                || player.getServer() == null || !player.getServer().isSameThread()
                || !player.getName().getString().equals("JoinDiagnostic")) return 0;
        var authority = BodyActionAuthority.resolve(player);
        if (authority.isEmpty()) {
            player.sendSystemMessage(Component.literal("Melee capture not ready; retry shortly."));
            return 0;
        }
        var body = authority.orElseThrow().body();
        try {
            if (peer != null && peerActive == null) {
                setPending(player, phase);
                player.sendSystemMessage(Component.literal("Melee capture waiting for registered peer lifecycle."));
                return 0;
            }
            var level = player.serverLevel();
            if (!motionObserverSwing) platform(level, body.position());
            boolean strikeMode = phase.startsWith("motion_");
            boolean observer = phase.startsWith("observer_") || motionObserverSetup || motionObserverSwing;
            if (phase.equals("motion_disarm_first") || phase.equals("motion_disarm_flight")) {
                var current = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                        .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable"));
                if (current.kind() == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD) {
                    var dropped = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.drop(player, current.activeHand(), current.itemToken().orElseThrow(), current.handRevision(),
                            entityId -> quarantineFixtureDrop(level, entityId));
                    if (dropped != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                        throw new IllegalStateException("canonical weapon drop failed: " + dropped);
                    current = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                            .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable after drop"));
                }
                if (current.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED)
                    throw new IllegalStateException("disarm scene requires unarmed hands after drop");
            }
            if (phase.equals("motion_jug_hit")) {
                moveExistingPeerAway(body.position());
                if (motionJugTarget != null && motionJugTarget.isAlive()) motionJugTarget.discard();
                var current = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                        .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable"));
                if (current.kind() == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD) {
                    var dropped = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.drop(player, current.activeHand(), current.itemToken().orElseThrow(), current.handRevision(),
                            entityId -> quarantineFixtureDrop(level, entityId));
                    if (dropped != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                        throw new IllegalStateException("canonical weapon drop failed: " + dropped);
                }
                current = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                        .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable after drop"));
                if (current.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED)
                    throw new IllegalStateException("jug scene requires unarmed hands after drop");
                motionJugTarget = com.juicyslew.moonstation14.entities.LooseItemEntity.create(level, body.position().add(0, .5, 1.2), new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()));
                motionJugTarget.setNoGravity(true);
                if (!level.addFreshEntity(motionJugTarget)) throw new IllegalStateException("production jug spawn failed");
                if (!com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility.damageable(motionJugTarget)) throw new IllegalStateException("production jug is not melee eligible");
            }
            if (phase.equals("motion_wall_clip")) {
                var current=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body).orElseThrow(() -> new IllegalStateException("primary melee selection unavailable"));
                if(current.kind()==com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD) {
                    var dropped=com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.drop(player,current.activeHand(),current.itemToken().orElseThrow(),current.handRevision(),entityId -> quarantineFixtureDrop(level,entityId));
                    if(dropped!=com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS) throw new IllegalStateException("canonical wall-scene weapon drop failed: "+dropped);
                }
                current=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body).orElseThrow(() -> new IllegalStateException("primary melee selection unavailable after wall-scene drop"));
                if(current.kind()!=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED) throw new IllegalStateException("wall scene requires unarmed hands");
            }
             if (phase.equals("motion_disarm_flight")) {
                  ensureDisarmPeer(level, body.position(), .9);
                  if (peerActive == null) { setPending(player, phase); player.sendSystemMessage(Component.literal("Melee capture waiting for registered peer lifecycle.")); return 0; }
                  equipWeapon(peer.player(), peerActive.body(), com.juicyslew.moonstation14.item.ModItems.CROWBAR.get());
              } else if (phase.equals("motion_observer_disarm_flight")) {
                  ensureDisarmPeer(level, body.position(), .9);
                  if (peerActive == null) { setPending(player, phase); player.sendSystemMessage(Component.literal("Melee capture waiting for registered peer lifecycle.")); return 0; }
                  equipWeapon(player, body, com.juicyslew.moonstation14.item.ModItems.CROWBAR.get());
                  peerActive.body().setYRot(180); peerActive.body().setYHeadRot(180);
              } else if (motionObserverSetup) { swingReply = null; }
              if (motionObserverSetup) flightExpectation = null;
              if (!phase.equals("motion_disarm_flight") && !phase.equals("motion_observer_disarm_flight") && (disarmScene(phase) || phase.equals("fist_hit_near") || phase.equals("fist_hit_edge"))) ensureDisarmPeer(level, body.position(), phase.equals("fist_hit_edge") ? 2.0 : .9);
              else if (observer && !motionObserverSwing) ensureObserver(level, body.position());
              if (motionObserverSetup && peerActive != null && !motionScene.equals("motion_observer_disarm_flight")) setupMotionObserver(body, motionScene);
            else if (phase.equals("wall_first")) { moveExistingPeerAway(body.position()); placeWall(level, body.position()); }
            else {
                if (phase.equals("fist_first") || phase.equals("fist_third") || phase.equals("knife_first")
                        || phase.equals("motion_fist_first") || phase.equals("motion_fist_third")
                        || phase.startsWith("motion_knife_") || phase.startsWith("motion_crowbar_")
                        || phase.equals("motion_wall_clip")) moveExistingPeerAway(body.position());
                if (body instanceof net.minecraft.world.entity.Mob mob) { mob.setNoAi(true); mob.setNoGravity(true); }
                body.setNoGravity(true);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                if (phase.equals("motion_wall_clip")) placeWall(level, body.position());
                 if (phase.startsWith("motion_knife_") || phase.equals("knife_first")) equipKnife(player, body);
                 if (phase.startsWith("motion_crowbar_")) equipWeapon(player, body, com.juicyslew.moonstation14.item.ModItems.CROWBAR.get());
                 if (phase.equals("motion_crowbar_wall_wide")) placeWall(level, body.position());
                 float scenePitch = phase.equals("motion_knife_wide_up") ? -60 : phase.equals("motion_knife_wide_vertical_up") ? -90 : phase.equals("motion_knife_wide_down") ? 60 : phase.equals("motion_knife_wide_vertical_down") ? 90 : 0;
                 body.setYRot(0); body.setYHeadRot(0); body.setXRot(scenePitch);
            }
            if ((observer || disarmScene(phase) || phase.equals("fist_hit_near") || phase.equals("fist_hit_edge")) && peerActive == null) {
                setPending(player, phase);
                player.sendSystemMessage(Component.literal("Melee capture waiting for registered peer lifecycle."));
                return 0;
            }
            if (observer && !motionObserverSwing) {
                body.setNoGravity(true);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            }
            if (phase.equals("motion_jug_hit")) {
                var delta = motionJugTarget.getBoundingBox().getCenter().subtract(body.getEyePosition());
                float yaw = (float)(Math.toDegrees(Math.atan2(-delta.x, delta.z)));
                float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
                body.setYRot(yaw); body.setYHeadRot(yaw); body.setXRot(pitch);
            }
            if (phase.equals("observer_swing")) swing(false);
            if (motionObserverSwing) swing(true, motionScene.equals("motion_observer_wide"), motionScene.equals("motion_observer_disarm_flight") ? body.getUUID() : null);
            var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                    .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable"));
            boolean held = selection.kind() == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD;
             boolean observerFlight = phase.equals("motion_observer_disarm_flight");
             if (phase.equals("motion_disarm_flight") && selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED)
                 throw new IllegalStateException("primary flight actor must be unarmed");
             if (observerFlight && (selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD
                     || !selection.itemToken().isPresent())) throw new IllegalStateException("observer flight victim must hold crowbar");
             boolean weaponScene = phase.equals("knife_first") || phase.startsWith("motion_knife_") || phase.startsWith("motion_crowbar_") || observerFlight;
            if (weaponScene && !held) throw new IllegalStateException("canonical weapon pickup did not select held weapon");
            if (!weaponScene && !observer && selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED)
                throw new IllegalStateException("expected empty hands, got " + selection.kind());
            Path dir = Path.of(System.getProperty("ms14.meleePresentationCaptureDir"));
            Files.createDirectories(dir);
            JsonObject json = new JsonObject();
            json.addProperty("protocol", 1); json.addProperty("phase", phase); json.addProperty("ready", true);
            json.addProperty("primaryBodyId", body.getId()); json.addProperty("primaryBodyUUID", body.getUUID().toString());
            json.addProperty("primaryEpoch", authority.orElseThrow().identity().epoch());
            json.addProperty("bodyX", body.getX()); json.addProperty("bodyY", body.getY()); json.addProperty("bodyZ", body.getZ());
            var look = body.getLookAngle();
            json.addProperty("lookX", look.x); json.addProperty("lookY", look.y); json.addProperty("lookZ", look.z);
            json.addProperty("expectedKind", held ? "HELD" : "UNARMED");
             var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
             if (hands == null) throw new IllegalStateException("body has no LiveHands");
             String actualItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hands.stackCopy(hands.activeHand()).orElse(net.minecraft.world.item.ItemStack.EMPTY).getItem()).toString();
              json.addProperty("actualItem", actualItem);
              json.addProperty("selectionHandRevision", selection.handRevision());
              json.addProperty("activeHand", selection.activeHand());
              json.addProperty("itemToken", selection.itemToken().orElse(""));
                 json.addProperty("expectedItem", observerFlight ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(com.juicyslew.moonstation14.item.ModItems.CROWBAR.get()).toString() : weaponScene ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                      phase.startsWith("motion_crowbar_") ? com.juicyslew.moonstation14.item.ModItems.CROWBAR.get() : com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get()).toString() : actualItem);
            if (observer || disarmScene(phase) || phase.equals("fist_hit_near") || phase.equals("fist_hit_edge")) {
                json.addProperty("peerBodyId", peerActive.body().getId()); json.addProperty("peerBodyUUID", peerActive.body().getUUID().toString()); json.addProperty("peerEpoch", peerActive.epoch());
                json.addProperty("peerX", peerActive.body().getX()); json.addProperty("peerY", peerActive.body().getY()); json.addProperty("peerZ", peerActive.body().getZ());
            }
            if (disarmScene(phase)) json.addProperty("targetUUID", peerActive.body().getUUID().toString());
            if (phase.equals("fist_hit_near") || phase.equals("fist_hit_edge")) { json.addProperty("targetId", peerActive.body().getId()); json.addProperty("targetUUID", peerActive.body().getUUID().toString()); }
              if (phase.equals("wall_first")) { var pos=new net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(body.getX()),net.minecraft.util.Mth.floor(body.getY())+1,net.minecraft.util.Mth.floor(body.getZ())+1); json.addProperty("targetBlockX",pos.getX()); json.addProperty("targetBlockY",pos.getY()); json.addProperty("targetBlockZ",pos.getZ()); json.addProperty("targetBlockStateId",net.minecraft.world.level.block.Block.getId(level.getBlockState(pos))); }
            if (observer) {
                json.addProperty("targetId", -1);
                json.addProperty("targetUUID", "");
            }
            if (phase.equals("observer_swing") || motionObserverSwing) {
                json.addProperty("replyStatus", swingReply.status().name());
                json.addProperty("replySequence", swingSequence);
                json.addProperty("replyServerTick", level.getGameTime());
                json.addProperty("cooldownRemainingTicks", swingReply.cooldownRemainingTicks());
            }
            if (phase.equals("motion_wall_clip") || phase.equals("motion_crowbar_wall_wide")) { var pos=new net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(body.getX()),net.minecraft.util.Mth.floor(body.getY())+1,net.minecraft.util.Mth.floor(body.getZ())+1); json.addProperty("targetBlockX",pos.getX()); json.addProperty("targetBlockY",pos.getY()); json.addProperty("targetBlockZ",pos.getZ()); json.addProperty("targetBlockStateId",net.minecraft.world.level.block.Block.getId(level.getBlockState(pos))); json.addProperty("expectedRequest", "HIT"); }
            if (phase.equals("motion_jug_hit")) { json.addProperty("targetEntityId", motionJugTarget.getId()); json.addProperty("targetEntityUUID", motionJugTarget.getUUID().toString()); json.addProperty("targetX", motionJugTarget.getX()); json.addProperty("targetY", motionJugTarget.getY()); json.addProperty("targetZ", motionJugTarget.getZ()); json.addProperty("expectedRequest", "HIT"); }
             if (motionObserverSetup || motionObserverSwing) {
                json.addProperty("phase", phase); json.addProperty("scene", motionScene);
                 json.addProperty("mode", motionScene.equals("motion_observer_wide") ? "WIDE" : motionScene.equals("motion_observer_disarm_flight") ? "DISARM" : "LIGHT");
                 json.addProperty("peerPitch", peerActive.body().getXRot()); json.addProperty("peerYaw", peerActive.body().getYRot());
                  json.addProperty("peerExpectedSwingSequence", nextExpectedSwingSequence(swingSequence, motionObserverSwing));
                 json.addProperty("peerSwingEpoch", peerActive.epoch());
             }
               if (phase.equals("motion_disarm_flight") || phase.equals("motion_observer_disarm_flight")) {
                   json.addProperty("scene", phase); json.addProperty("mode", "DISARM");
                   json.addProperty("requireCommittedFlight", true);
                   // Shared entity identities make the setup scene an explicit contract for both actor roles.
                   writeFlightSceneIdentity(json, observerFlight, body.getId(), body.getUUID().toString(), authority.orElseThrow().identity().epoch(), peerActive.body().getId(), peerActive.body().getUUID().toString(), peerActive.epoch());
                   json.addProperty("targetPeerUUID", (phase.equals("motion_disarm_flight") ? peerActive.body() : body).getUUID().toString());
                   java.util.UUID expectedActor = phase.equals("motion_observer_disarm_flight") ? peerActive.body().getUUID() : body.getUUID();
                   java.util.UUID expectedVictim = phase.equals("motion_observer_disarm_flight") ? body.getUUID() : peerActive.body().getUUID();
                   json.addProperty("expectedActorUUID", expectedActor.toString()); json.addProperty("expectedVictimUUID", expectedVictim.toString());
                    if (!motionObserverSwing) { fixtureChanceSeed = -1L; flightExpectation = new FlightExpectation(expectedActor, expectedVictim, level.dimension(), level.getGameTime(), phase,
                             Set.of(player.getUUID(), peer.player().getUUID())); }
                   var expected = flightExpectation;
                    if (expected != null && expected.actor().equals(expectedActor) && expected.victim().equals(expectedVictim)
                           && lastFlightReceipt != null && lastFlightReceipt.world().equals(level.dimension())
                           && lastFlightReceipt.actorUUID().equals(expectedActor) && lastFlightReceipt.victimUUID().equals(expectedVictim)
                           && lastFlightReceipt.publishedTick() >= expected.setupTick()) { writeFlight(json, lastFlightReceipt); json.addProperty("releaseReady", true); }
              }
              if (phase.startsWith("motion_knife_") || phase.startsWith("motion_crowbar_")) {
                 json.addProperty("scene", phase);
                 json.addProperty("mode", phase.equals("motion_knife_first") || phase.equals("motion_knife_repeat") || phase.equals("motion_crowbar_light") ? "LIGHT" : "WIDE");
                 json.addProperty("pitch", body.getXRot());
                 json.addProperty("lookX", body.getLookAngle().x); json.addProperty("lookY", body.getLookAngle().y); json.addProperty("lookZ", body.getLookAngle().z);
             }
             Path target = dir.resolve("scene.json");
             currentScenePath = target;
             atomicSceneWrite(target, json);
            player.sendSystemMessage(Component.literal("Melee capture phase recorded: " + phase));
            return 1;
        } catch (Exception failure) {
            System.err.println("MS14_MELEE_CAPTURE_FAILED phase=" + phase + " error=" + failure);
            terminalFailure(phase, failure);
            player.sendSystemMessage(Component.literal("Melee capture failed: " + failure.getMessage()));
            return 0;
        }
    }

    private static void platform(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 center) {
        int cx=net.minecraft.core.BlockPos.containing(center).getX(), cz=net.minecraft.core.BlockPos.containing(center).getZ();
        int floorY=net.minecraft.core.BlockPos.containing(center).getY()-1;
        for (int x=cx-8;x<=cx+8;x++) for (int z=cz-8;z<=cz+8;z++) {
            level.setBlock(new net.minecraft.core.BlockPos(x,floorY,z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            for (int y=floorY+1;y<=floorY+5;y++) level.setBlock(new net.minecraft.core.BlockPos(x,y,z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void equipKnife(ServerPlayer owner, net.minecraft.world.entity.LivingEntity body) {
        equipWeapon(owner, body, com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get());
    }

    static void equipWeapon(ServerPlayer owner, net.minecraft.world.entity.LivingEntity body, net.minecraft.world.item.Item item) {
        var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null) throw new IllegalStateException("body has no LiveHands");
        var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body).orElse(null);
        var snapshot = hands.stackCopy(hands.activeHand());
        if (selection != null && selection.kind() == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(snapshot.orElse(net.minecraft.world.item.ItemStack.EMPTY).getItem()).equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))) return;
            var dropped = com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.drop(owner, selection.activeHand(), selection.itemToken().orElseThrow(), selection.handRevision(),
                    entityId -> quarantineFixtureDrop(owner.serverLevel(), entityId));
            if (dropped != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                throw new IllegalStateException("canonical replacement weapon drop failed: " + dropped);
            hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            if (hands == null) throw new IllegalStateException("body has no LiveHands after drop");
        }
         var stack = new net.minecraft.world.item.ItemStack(item);
         var loose = com.juicyslew.moonstation14.entities.LooseItemEntity.create(owner.serverLevel(), body.getEyePosition().add(body.getLookAngle().scale(.7)), stack);
         loose.setNoGravity(true);
         var beforeRevision = hands.revision();
         if (!owner.serverLevel().addFreshEntity(loose) || com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(owner, loose.getUUID(), hands.activeHand(), hands.revision(), ignored -> {})
                  != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS) throw new IllegalStateException("canonical weapon pickup failed");
         var currentHands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
         if (currentHands == null) throw new IllegalStateException("body has no LiveHands after canonical weapon pickup");
         var actual = currentHands.stackCopy(currentHands.activeHand());
         var expectedId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
         var actualId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.orElse(net.minecraft.world.item.ItemStack.EMPTY).getItem());
          if (!actualId.equals(expectedId))
              throw new IllegalStateException("canonical weapon pickup selected unexpected item: expected=" + expectedId + " actual=" + actualId
                      + " revisionBefore=" + beforeRevision + " revisionAfter=" + currentHands.revision());
          var committedSelection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                  .orElseThrow(() -> new IllegalStateException("primary melee selection unavailable after canonical weapon pickup"));
           var token = committedSelection.itemToken();
          if (committedSelection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD
                  || !committedSelection.activeHand().equals(currentHands.activeHand())
                  || committedSelection.handRevision() != currentHands.revision()
                   || token.isEmpty() || !token.equals(currentHands.token(currentHands.activeHand()))
                  || currentHands.revision() <= beforeRevision)
              throw new IllegalStateException("canonical weapon pickup selection is incoherent: expected=" + expectedId
                      + " selectionKind=" + committedSelection.kind() + " selectionRevision=" + committedSelection.handRevision()
                      + " revisionBefore=" + beforeRevision + " revisionAfter=" + currentHands.revision());
    }

    private static void quarantineFixtureDrop(net.minecraft.server.level.ServerLevel level, java.util.UUID entityId) {
        var entity = level.getEntity(entityId);
        if (entity == null) throw new IllegalStateException("committed fixture drop entity was not published");
        entity.setPos(6, 0, 6);
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    private static void ensureObserver(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 primary) {
        ensurePeer(level);
        if (peerActive == null) return;
        peerActive.body().setPos(primary.x+1.0, primary.y, primary.z+3.0); peerActive.body().setYRot(-90); peerActive.body().setYHeadRot(-90); peerActive.body().setXRot(0);
        if (peerActive.body() instanceof net.minecraft.world.entity.Mob mob) { mob.setNoAi(true); mob.setNoGravity(true); }
    }

    record MotionObserverPose(double offsetX, double offsetY, double offsetZ, float yaw, float pitch) { }

    static MotionObserverPose resolveMotionObserverPose(String scene) {
        return switch (scene) {
            case "motion_observer_up" -> new MotionObserverPose(1.0, 0, 3.0, -90, -45);
            case "motion_observer_down" -> new MotionObserverPose(1.0, 0, 3.0, -90, 45);
            case "motion_observer_close" -> new MotionObserverPose(-.45, 0, .75, -60, 0);
            default -> new MotionObserverPose(1.0, 0, 3.0, -90, 0);
        };
    }

    static int nextExpectedSwingSequence(int current, boolean swingingNow) {
        return swingingNow ? current : current + 1;
    }

    private static void setupMotionObserver(net.minecraft.world.entity.LivingEntity primary, String scene) {
        var observer = peerActive.body();
        var pose = resolveMotionObserverPose(scene);
        observer.setPos(primary.getX() + pose.offsetX(), primary.getY() + pose.offsetY(), primary.getZ() + pose.offsetZ());
        observer.setYRot(pose.yaw()); observer.setYHeadRot(pose.yaw());
        observer.setXRot(pose.pitch());
        observer.setNoGravity(true);
        if (observer instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
    }

    private static void ensureDisarmPeer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 primary, double offset) {
        ensurePeer(level);
        if (peerActive == null) return;
        peerActive.body().setPos(primary.x, primary.y, primary.z + offset);
        peerActive.body().setYRot(0); peerActive.body().setYHeadRot(0); peerActive.body().setXRot(0);
        peerActive.body().setNoGravity(true);
        if (peerActive.body() instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
    }

    private static void placeWall(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 primary) {
        int x=net.minecraft.util.Mth.floor(primary.x), y=net.minecraft.util.Mth.floor(primary.y)+1, z=net.minecraft.util.Mth.floor(primary.z)+1;
        for(int dy=0;dy<2;dy++) level.setBlock(new net.minecraft.core.BlockPos(x,y+dy,z), com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
    }

    private static void ensurePeer(net.minecraft.server.level.ServerLevel level) {
        if (peer != null && peerActive == null) return;
        if (peer != null && peer.isRegisteredAndAlive() && peerActive != null && peerActive.body().isAlive()) {
            var authority = BodyActionAuthority.resolve(peer.player());
            if (authority.isPresent() && authority.orElseThrow().body() == peerActive.body()
                    && authority.orElseThrow().identity().epoch() == peerActive.epoch()) return;
        }
        if (peer != null) { peer.close(); peer = null; peerActive = null; }
        peer = RegisteredServerPlayerFixture.register(level, "MeleePeer");
        peer.observeOutbound(packet -> {
            if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Begin begin
                    && begin.harnessKind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER) peerBegin = begin;
        });
        peerActive = null;
        swingReply = null;
    }

    private static void clearPending() { pendingOwner = null; pendingPhase = null; pendingDeadline = 0; peerBegin = null; }

    private static void setPending(ServerPlayer owner, String phase) {
        if (owner.getUUID().equals(pendingOwner) && phase.equals(pendingPhase)) return;
        pendingOwner = owner.getUUID(); pendingPhase = phase; pendingDeadline = diagnosticTicks + 200;
    }

    private static void terminalFailure(String phase, Exception failure) {
        System.err.println("MS14_MELEE_CAPTURE_FAILED phase=" + phase + " state=peer_begin error=" + failure);
        try {
            Path dir = Path.of(System.getProperty("ms14.meleePresentationCaptureDir")); Files.createDirectories(dir);
            JsonObject json = new JsonObject(); json.addProperty("status", "FAILED"); json.addProperty("reason", failure.toString()); json.addProperty("phase", phase);
            Files.writeString(dir.resolve("melee-capture-failed.json"), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
        clearPending();
        if (peer != null) { peer.close(); peer = null; peerActive = null; }
    }

    private static void moveExistingPeerAway(net.minecraft.world.phys.Vec3 primary) {
        if (peer == null || !peer.isRegisteredAndAlive() || peerActive == null || !peerActive.body().isAlive()) return;
        peerActive.body().setPos(primary.x + 6.0, primary.y, primary.z);
    }

    private static void swing(boolean motion) { swing(motion, false); }

     private static void swing(boolean motion, boolean wide) {
         swing(motion, wide, null);
     }

     private static void atomicSceneWrite(Path target, JsonObject json) throws java.io.IOException {
         Path temporary = target.resolveSibling("scene.json.tmp");
         Files.writeString(temporary, json.toString(), StandardCharsets.UTF_8);
         try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
         catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
     }
     private static String baseScene(String scene) { return scene.endsWith("_swing") ? scene.substring(0, scene.length() - 6) : scene; }
     private static void swing(boolean motion, boolean wide, java.util.UUID disarmTarget) {
        if (swingReply != null && !motion) return;
        if (motion && swingReply != null) return;
        swingSequence = motion ? swingSequence + 1 : 1;
        var player = peer.player(); var body = peerActive.body();
        com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(player,
                new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(peerActive.epoch(),swingSequence,true));
        var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body)
                .orElseThrow(() -> new IllegalStateException("peer selection unavailable"));
        swingReply = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(player,
                disarmTarget != null
                        ? new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(peerActive.epoch(),swingSequence,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.DISARM,java.util.List.of(new com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Entity(disarmTarget)),false)
                        : wide
                        ? new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(peerActive.epoch(),swingSequence,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE,null,java.util.List.of())
                        : new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(peerActive.epoch(),swingSequence,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),(java.util.UUID)null));
        if (disarmTarget == null && swingReply.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS)
            throw new IllegalStateException("observer swing was not an authoritative miss: " + swingReply.status());
    }
    private static void writeFlight(JsonObject json, com.juicyslew.moonstation14.ms14.combat.MeleeAttackEvents.DisarmFlightPublishedEvent receipt) {
        var flight = new JsonObject();
        flight.addProperty("itemUUID", receipt.flight().entityId().toString());
        flight.addProperty("actorUUID", receipt.actorUUID().toString()); flight.addProperty("victimUUID", receipt.victimUUID().toString());
        var origin = new JsonObject(); origin.addProperty("x", receipt.flight().origin().x); origin.addProperty("y", receipt.flight().origin().y); origin.addProperty("z", receipt.flight().origin().z);
        var velocity = new JsonObject(); velocity.addProperty("x", receipt.flight().velocity().x); velocity.addProperty("y", receipt.flight().velocity().y); velocity.addProperty("z", receipt.flight().velocity().z);
        flight.add("origin", origin); flight.add("velocity", velocity);
        var recipients = new com.google.gson.JsonArray(); receipt.recipientUUIDs().forEach(id -> recipients.add(id.toString()));
        flight.add("recipientUUIDs", recipients); flight.addProperty("publishedTick", receipt.publishedTick());
        json.add("committedFlight", flight);
        json.addProperty("fixtureChanceSeed", fixtureChanceSeed);
    }
}
