package com.juicyslew.moonstation14.ms14.combat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Opt-in bounded visual diagnostic using the ordinary combat input path. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class MeleeStrikePresentationClientDiagnostic {
    private static final boolean ENABLED = Boolean.getBoolean("ms14.meleeStrikePresentationDiagnostic");
    private static final String[] SCENES = {"motion_fist_first", "motion_fist_third", "motion_observer_up", "motion_observer_down", "motion_observer_close", "motion_observer_wide", "motion_knife_first", "motion_knife_wide", "motion_wall_clip", "motion_disarm_first", "motion_crowbar_light", "motion_jug_hit", "motion_knife_repeat", "motion_knife_wide_up", "motion_knife_wide_vertical_up", "motion_knife_wide_down", "motion_knife_wide_vertical_down", "motion_crowbar_wall_wide", "motion_disarm_flight", "motion_observer_disarm_flight"};
    private static final String[] PHASES = {"outward", "extended", "return"};
    private static final double[] LIGHT_WINDOWS = {.10, .40, .72};
    private static final double[] LIGHT_ENDS = {.30, .55, .92};
     private static final double[] WIDE_WINDOWS = {.10, .18, .72};
     private static final double[] WIDE_ENDS = {.16, .24, .92};
    private static final JsonObject[][] CAPTURES = new JsonObject[SCENES.length][3];
    private static final List<JsonObject> records = new ArrayList<>();
    private static final List<JsonObject> frameHistory = new ArrayList<>(64);
    private static JsonObject preInputObservation;
    private static Double firstObservedProgress, matchingProgress;
    private static boolean matchingWindowObserved;
    private static Path dir;
    private static int sceneIndex, sampleIndex, ticks, callbacks;
    private static boolean started, armed, attackSent, replyReady, done, warming = true;
    private static JsonObject warmupReply;
    private static long warmupReadyAt;
    private static boolean warmupAttemptUsed;
    private static int stableRenderIntervals;
    private static long lastRenderNanos, lastRenderIntervalNanos;
    private static final long MAX_STABLE_INTERVAL_NANOS = 50_000_000L;
    private static final int REQUIRED_STABLE_INTERVALS = 12;
    private static long watermark, sequence;
    private static UUID body;
    private static JsonObject scene, serverReply;
    private static String stage = "setup";
     private static long lastRefresh;
     private static String admissionBlock = "not-attempted";
     private static JsonObject jugReadinessSnapshot = new JsonObject();
     private static JsonObject combatReadinessSnapshot = new JsonObject();
     private static final List<JsonObject> combatReadinessHistory = new ArrayList<>(16);
    private static Boolean originalHideGui;
    private MeleeStrikePresentationClientDiagnostic() { }
     private static boolean observer() { return SCENES[sceneIndex].startsWith("motion_observer"); }
     private static boolean requiresFlightScene(String name) { return name.equals("motion_disarm_flight") || name.equals("motion_observer_disarm_flight"); }
      private static boolean requiresFlight() { return SCENES[sceneIndex].equals("motion_disarm_flight") || SCENES[sceneIndex].equals("motion_observer_disarm_flight"); }
     private static boolean disarmScene() { return SCENES[sceneIndex].equals("motion_disarm_first") || requiresFlight(); }
     private static JsonObject flightObservation = new JsonObject();
     private static boolean flightEvidenceReady(long now) throws Exception {
         flightObservation = new JsonObject();
         Path p=dir.resolve("scene.json"); if(!Files.isRegularFile(p)) return flightFail("missing-scene");
         JsonObject fresh=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
         flightObservation.add("scene",fresh.deepCopy());
         if(!fresh.has("releaseReady")||!fresh.get("releaseReady").getAsBoolean()||!fresh.has("committedFlight")) return flightFail("release-not-committed");
          JsonObject f=fresh.getAsJsonObject("committedFlight");
         String base=SCENES[sceneIndex].replace("motion_observer_","").replace("motion_","");
          String phase=fresh.has("phase")?fresh.get("phase").getAsString():fresh.get("scene").getAsString();
           if(phase.endsWith("_swing")) phase=phase.substring(0,phase.length()-6);
           if(!phase.equals(SCENES[sceneIndex])) return flightFail("phase-mismatch");
           String metadataFailure=flightMetadataFailure(scene,f,SCENES[sceneIndex]);
           if(metadataFailure!=null) return flightFail(metadataFailure);
         UUID item=UUID.fromString(f.get("itemUUID").getAsString()); var cue=DisarmFlightVisuals.snapshotByItem(now).get(item);
          JsonObject origin=f.getAsJsonObject("origin"), velocity=f.getAsJsonObject("velocity");
          if(!flightCueMatches(cue, origin, velocity, sampleIndex)) return flightFail(cue==null?"no-active-participant-cue":"cue-mismatch");
          UUID profile=mc().player.getUUID(); boolean recipient=false; for(var value:f.getAsJsonArray("recipientUUIDs")) if(value.getAsString().equals(profile.toString())) recipient=true;
          if(!recipient) return flightFail("not-recipient");
          scene.add("committedFlight",f.deepCopy()); scene.addProperty("releaseReady",true);
          JsonObject meta=flightEvidenceRecord(cue,profile.toString(),item.toString(),origin,velocity,now);meta.addProperty("hudHidden",mc().options.hideGui); flightObservation=meta; return true;
     }
       static boolean flightCueMatches(DisarmFlightVisuals.Flight cue, JsonObject origin, JsonObject velocity, int sampleIndex) {
           double vx=velocity.get("x").getAsDouble(), vy=velocity.get("y").getAsDouble(), vz=velocity.get("z").getAsDouble();
           if(!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(vz)||vx*vx+vy*vy+vz*vz<1e-8) return false;
           if(cue==null) return sampleIndex>=2;
           return cue.participant() && Math.abs(cue.origin().x-origin.get("x").getAsDouble())<=1e-6 && Math.abs(cue.origin().y-origin.get("y").getAsDouble())<=1e-6 && Math.abs(cue.origin().z-origin.get("z").getAsDouble())<=1e-6 && Math.abs(cue.velocity().x-vx)<=1e-6 && Math.abs(cue.velocity().y-vy)<=1e-6 && Math.abs(cue.velocity().z-vz)<=1e-6 && cue.velocity().lengthSqr()>=1e-8;
      }
      public static UUID validateFlightMetadata(JsonObject scene, JsonObject flight, String role) {
          for(String key:new String[]{"peerBodyId","peerBodyUUID","peerEpoch","primaryBodyId","primaryBodyUUID","primaryEpoch","targetId","targetUUID","expectedActorUUID","expectedVictimUUID"})
              if(!scene.has(key)||scene.get(key).isJsonNull()) throw new IllegalStateException("missing-"+key);
          boolean observer=role.equals("motion_observer_disarm_flight");
          String actor=observer?scene.get("peerBodyUUID").getAsString():scene.get("primaryBodyUUID").getAsString();
          String victim=observer?scene.get("primaryBodyUUID").getAsString():scene.get("peerBodyUUID").getAsString();
          if(!actor.equals(scene.get("expectedActorUUID").getAsString())||!victim.equals(scene.get("expectedVictimUUID").getAsString())) throw new IllegalStateException("actor-victim-metadata-mismatch");
          if(!victim.equals(scene.get("targetUUID").getAsString())) throw new IllegalStateException("wrong-target-role-expected-victim");
          if(flight!=null&&(!flight.has("actorUUID")||!flight.has("victimUUID")||!actor.equals(flight.get("actorUUID").getAsString())||!victim.equals(flight.get("victimUUID").getAsString()))) throw new IllegalStateException("actor-victim-mismatch");
          return UUID.fromString(victim);
      }
      static String flightMetadataFailure(JsonObject scene, JsonObject flight, String role) { try { validateFlightMetadata(scene,flight,role); return null; } catch(IllegalStateException e) { return e.getMessage(); } }
      private static boolean flightFail(String reason){flightObservation.addProperty("failure",reason);return false;}
    private static boolean knife() { return SCENES[sceneIndex].startsWith("motion_knife"); }
      private static boolean third() { return thirdPersonScene(SCENES[sceneIndex]); }
       static boolean thirdPersonScene(String name) { return !name.startsWith("motion_observer") && (name.equals("motion_fist_third") || name.equals("motion_knife_wide") || name.equals("motion_crowbar_light")); }
      static boolean isWideScene(String name) { return name.equals("motion_observer_wide") || name.equals("motion_knife_wide") || name.equals("motion_crowbar_wall_wide") || name.matches("motion_knife_wide_(up|down|vertical_up|vertical_down)"); }
      static double windowStart(String name, int phase) { return (sceneMode(name)==com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE ? WIDE_WINDOWS : LIGHT_WINDOWS)[phase]; }
        static double windowEnd(String name, int phase) { return (sceneMode(name)==com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE ? WIDE_ENDS : LIGHT_ENDS)[phase]; }
      static boolean cameraAimReady(double cameraDot, boolean frontMirror) { return Double.isFinite(cameraDot) && (frontMirror ? cameraDot < -.995 : cameraDot > .995); }
      static boolean safeSceneWarmup(String name) {
          return name.startsWith("motion_observer_") && !requiresFlightScene(name)
                  || (name.startsWith("motion_knife") || name.equals("motion_fist_first") || name.equals("motion_fist_third") || name.equals("motion_crowbar_light"))
                  && !name.contains("repeat");
      }
       static boolean peerCooldownReady(double confirmedTick, long deadline) { return confirmedTick >= deadline; }
        static JsonObject flightEvidenceRecord(DisarmFlightVisuals.Flight cue,String receiver,String item,JsonObject origin,JsonObject velocity,long now) { JsonObject m=new JsonObject();m.addProperty("active",cue!=null);m.addProperty("participant",cue!=null&&cue.participant());m.addProperty("receiverUUID",receiver);m.addProperty("itemUUID",item);m.add("origin",origin.deepCopy());m.add("velocity",velocity.deepCopy());m.addProperty("ageNanos",cue==null?-1:now-cue.started());m.addProperty("lifetimeNanos",DisarmFlightVisuals.CUE_LIFETIME_NANOS);return m; }
       static boolean observerReplyMatches(JsonObject setup,JsonObject r,String name) { return r.has("phase")&&(name+"_swing").equals(r.get("phase").getAsString())&&r.has("peerBodyUUID")&&setup.get("peerBodyUUID").getAsString().equals(r.get("peerBodyUUID").getAsString())&&r.has("peerEpoch")&&r.get("peerEpoch").getAsLong()==setup.get("peerEpoch").getAsLong()&&r.has("replySequence")&&setup.has("peerExpectedSwingSequence")&&r.get("replySequence").getAsLong()==setup.get("peerExpectedSwingSequence").getAsLong()&&r.has("peerSwingEpoch")&&r.get("peerSwingEpoch").getAsLong()==setup.get("peerEpoch").getAsLong()&&r.has("scene")&&name.equals(r.get("scene").getAsString()); }
      static boolean missedWindow(double progress, String name, int phase) { return progress > windowEnd(name, phase); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
             if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
             if (started) MeleeCaptureFocusPolicy.require(mc);
            if (originalHideGui == null) {
                originalHideGui = mc.options.hideGui;
                mc.options.hideGui = !requiresFlight();
            }
            mc.getToasts().clear();
            if (dir == null) { String p=System.getProperty("ms14.meleePresentationCaptureDir"); if(p==null||p.isBlank()) throw new IllegalStateException("missing capture directory"); dir=Path.of(p).toAbsolutePath().normalize(); Files.createDirectories(dir); }
            if (!started) {
                if (com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation()==null || !profilesReady()) { if(++ticks>200) throw new IllegalStateException("owned body/catalog readiness timeout"); return; }
                 started=true; MeleeCaptureFocusPolicy.require(mc); next(mc); return;
            }
            if (++ticks > 18*20) throw new IllegalStateException("scene deadline expired stage="+stage+" scene="+SCENES[sceneIndex]);
            if (scene==null) {
                Path p=dir.resolve("scene.json"); if(!Files.isRegularFile(p)) return;
                JsonObject candidate=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if(!candidate.has("ready")||!candidate.get("ready").getAsBoolean()||!SCENES[sceneIndex].equals(candidate.get("phase").getAsString())) return;
                scene=candidate; mc.options.setCameraType(observer()||!third()?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_FRONT);
                if(observer()) { armObserver(mc); return; }
            }
             if(observer()&&!attackSent) { armObserver(mc); return; }
            if(!observer()&&!attackSent) { if(stableRenderIntervals>=REQUIRED_STABLE_INTERVALS) attack(mc); }
             checkReply();
             if (warming && replyReady && MeleeArcClient.diagnosticSnapshot().queuedCount() == 0
                     && stableRenderIntervals >= REQUIRED_STABLE_INTERVALS) finishWarmup(mc);
          if(!warming && replyReady && callbacks==3) advance(mc);
        } catch(Throwable t) { fail(t); }
    }
    private static boolean profilesReady() { var c=com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog(); return c.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_arc_fist"))!=null && c.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_arc_slash"))!=null && c.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_arc_thrust"))!=null; }
        private static void next(Minecraft mc) { MeleeCaptureFocusPolicy.require(mc); warming=safeSceneWarmup(SCENES[sceneIndex]); warmupAttemptUsed=false; mc.options.hideGui=!requiresFlight(); flightObservation=new JsonObject(); jugReadinessSnapshot=new JsonObject();combatReadinessSnapshot=new JsonObject();combatReadinessHistory.clear();stage="wait_ready"; ticks=0; stableRenderIntervals=0;lastRenderNanos=0;lastRenderIntervalNanos=0;frameHistory.clear();preInputObservation=null;firstObservedProgress=matchingProgress=null;matchingWindowObserved=false;watermark=MeleeArcClient.diagnosticSnapshot().frameId(); mc.getConnection().sendCommand("ms14_melee_capture "+SCENES[sceneIndex]); }
      private static void finishWarmup(Minecraft mc) {
          MeleeCaptureFocusPolicy.require(mc);
          warmupReply = serverReply == null ? new JsonObject() : serverReply.deepCopy();
          scene = null; attackSent = armed = replyReady = false; serverReply = null;
         sampleIndex = callbacks = 0; frameHistory.clear(); preInputObservation = null;
         firstObservedProgress = matchingProgress = null; matchingWindowObserved = false;
         stableRenderIntervals = 0; lastRenderNanos = 0; warming = false; ticks = 0;
         if (observer()) mc.getConnection().sendCommand("ms14_melee_capture " + SCENES[sceneIndex]);
         stage = "wait_ready_measured";
     }
      private static void armObserver(Minecraft mc) {
          MeleeCaptureFocusPolicy.require(mc);
         if(requiresFlight()) validateFlightMetadata(scene,null,SCENES[sceneIndex]);
         var peer=mc.level.getEntity(scene.get("peerBodyId").getAsInt()); if(peer==null||!peer.getUUID().toString().equals(scene.get("peerBodyUUID").getAsString())) return;
        var pos=peer.position(); double dx=pos.x-scene.get("peerX").getAsDouble(),dy=pos.y-scene.get("peerY").getAsDouble(),dz=pos.z-scene.get("peerZ").getAsDouble();
        if(dx*dx+dy*dy+dz*dz>.0625||MeleeArcClient.diagnosticSnapshot().queuedCount()!=0) return;
         if (stableRenderIntervals < REQUIRED_STABLE_INTERVALS) return;
          if (observer() && !peerCooldownReady(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick().orElse(Long.MIN_VALUE), warmupReadyAt)) return;
          if (warming && warmupAttemptUsed) return;
          if (warming) warmupAttemptUsed=true;
         watermark=MeleeArcClient.diagnosticSnapshot().frameId(); body=peer.getUUID(); armed=attackSent=true; stage="wait_reply"; mc.getConnection().sendCommand("ms14_melee_capture "+SCENES[sceneIndex]+"_swing");
    }
       private static boolean sceneAimReady(Minecraft mc) {
          var desired=new net.minecraft.world.phys.Vec3(scene.get("lookX").getAsDouble(),scene.get("lookY").getAsDouble(),scene.get("lookZ").getAsDouble()).normalize();
           if(!scene.has("aimApplied")) { float yaw=(float)Math.toDegrees(Math.atan2(-desired.x,desired.z)); float pitch=(float)-Math.toDegrees(Math.asin(desired.y)); mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.projectOwnedGhostLookForRenderFrame();scene.addProperty("aimApplied",true);stableRenderIntervals=0;lastRenderNanos=0;return false; }
           var body=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();if(body==null){jugReadinessSnapshot=new JsonObject();jugReadinessSnapshot.addProperty("reason","missing-controlled-body");return false;}var cam=mc.gameRenderer.getMainCamera();var desiredVector=new org.joml.Vector3f((float)desired.x,(float)desired.y,(float)desired.z);double bd=body.getLookAngle().normalize().dot(desired),cd=cam.getLookVector().normalize().dot(desiredVector);boolean frontMirror=mc.options.getCameraType()==CameraType.THIRD_PERSON_FRONT;jugReadinessSnapshot=new JsonObject();jugReadinessSnapshot.addProperty("desiredLook",desired.toString());jugReadinessSnapshot.addProperty("bodyLook",body.getLookAngle().toString());jugReadinessSnapshot.addProperty("cameraLook",cam.getLookVector().toString());jugReadinessSnapshot.addProperty("bodyDot",bd);jugReadinessSnapshot.addProperty("cameraDot",cd);jugReadinessSnapshot.addProperty("cameraExpectedMirror",frontMirror);jugReadinessSnapshot.addProperty("cameraReady",cameraAimReady(cd,frontMirror));jugReadinessSnapshot.addProperty("stableRenderIntervals",stableRenderIntervals);jugReadinessSnapshot.addProperty("ready",stableRenderIntervals>=REQUIRED_STABLE_INTERVALS&&bd>.995&&cameraAimReady(cd,frontMirror));scene.add("aimReadiness",jugReadinessSnapshot.deepCopy());return jugReadinessSnapshot.get("ready").getAsBoolean();
       }
       private static boolean jugAimReady(Minecraft mc) {
            var target=jugCaptureTarget(mc,scene);
           if(target==null){jugReadinessSnapshot=new JsonObject();jugReadinessSnapshot.addProperty("reason","missing-target-entity");return false;}
          if(!sceneAimReady(mc))return false;
          var desired=new net.minecraft.world.phys.Vec3(scene.get("lookX").getAsDouble(),scene.get("lookY").getAsDouble(),scene.get("lookZ").getAsDouble()).normalize();
             var body=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();if(body==null){jugReadinessSnapshot=new JsonObject();jugReadinessSnapshot.addProperty("reason","missing-controlled-body");return false;}var cam=mc.gameRenderer.getMainCamera();var desiredVector=new org.joml.Vector3f((float)desired.x,(float)desired.y,(float)desired.z);double bd=body.getLookAngle().normalize().dot(desired),cd=cam.getLookVector().normalize().dot(desiredVector);double reach=com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.eyeRayReach(6.0,body.getBoundingBox());var rayTarget=com.juicyslew.moonstation14.ms14.standing.ClientBodyEntityRay.nearest(body,reach,com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility::damageable,e->e.getBoundingBox());boolean ray=rayTarget!=null&&target.getUUID().equals(rayTarget);var profile=com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.resolve().orElse(null);var arc=com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","weapon_arc_fist"));var stack=profile==null?java.util.Optional.<net.minecraft.world.item.ItemStack>empty():com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.presentationStack(profile);boolean profileReady=jugSelectionReady(profile==null?null:profile.kind(),stack.isPresent(),arc!=null);jugReadinessSnapshot=new JsonObject();jugReadinessSnapshot.addProperty("desiredLook",desired.toString());jugReadinessSnapshot.addProperty("bodyLook",body.getLookAngle().toString());jugReadinessSnapshot.addProperty("cameraLook",cam.getLookVector().toString());jugReadinessSnapshot.addProperty("bodyDot",bd);jugReadinessSnapshot.addProperty("cameraDot",cd);jugReadinessSnapshot.addProperty("stableRenderIntervals",stableRenderIntervals);jugReadinessSnapshot.addProperty("targetUUID",target.getUUID().toString());jugReadinessSnapshot.addProperty("rayTargetUUID",rayTarget==null?"missing":rayTarget.toString());jugReadinessSnapshot.addProperty("targetDamageable",com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility.damageable(target));jugReadinessSnapshot.addProperty("selectedKind",profile==null?"missing":profile.kind().name());jugReadinessSnapshot.addProperty("stackRequired",profile==null||profile.kind()!=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED);jugReadinessSnapshot.addProperty("stackPresent",stack.isPresent());jugReadinessSnapshot.addProperty("arcReady",arc!=null);jugReadinessSnapshot.addProperty("ready",stableRenderIntervals>=REQUIRED_STABLE_INTERVALS&&bd>.995&&cd>.995&&ray&&profileReady);scene.add("jugReadiness",jugReadinessSnapshot.deepCopy());return jugReadinessSnapshot.get("ready").getAsBoolean();
      }
      static boolean jugSelectionReady(com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind kind, boolean stackReady, boolean arcReady) {
          return kind != null && arcReady && (kind == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED || stackReady);
      }
     private static void attack(Minecraft mc) {
        var controlled=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        if(controlled==null||controlled.getId()!=scene.get("primaryBodyId").getAsInt()||!controlled.getUUID().toString().equals(scene.get("primaryBodyUUID").getAsString())) return;
        body=controlled.getUUID(); long epoch=scene.get("primaryEpoch").getAsLong();
         if (!prepareBeforeAdmission(() -> MeleeCaptureFocusPolicy.require(mc), () -> {
                if (!com.juicyslew.moonstation14.ms14.combat.CombatModeClient.projectedMode()) {
                    admissionBlock="combat-mode";
                    boolean pending=com.juicyslew.moonstation14.ms14.combat.CombatModeClient.policy().pending();
                    boolean attempted=!pending;
                    Boolean returned=attempted?com.juicyslew.moonstation14.ms14.combat.CombatModeClient.requestToggle():null;
                    recordCombatReadiness(mc, controlled, epoch, attempted, returned, pending);
                    return false;
                }
                recordCombatReadiness(mc, controlled, epoch, false, null, false);
                return true;
           })) return;
        var hand=com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        if(hand==null||!hand.bodyId().equals(body)||hand.epoch()!=epoch||com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick().isEmpty()) { long now=System.nanoTime(); if(now-lastRefresh>1_000_000_000L){com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();lastRefresh=now;} return; }
          var selection=com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.resolve().orElse(null);
         var kind=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.valueOf(scene.get("expectedKind").getAsString());
          if(selection==null||selection.kind()!=kind) { admissionBlock="weapon-selection"; return; }
         if (!scene.has("expectedItem") || scene.get("expectedItem").isJsonNull()) throw new IllegalStateException("scene missing expectedItem");
          String expectedItem=scene.get("expectedItem").getAsString();
          String actualItem;
          if (selection.kind()==com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED) {
              actualItem="minecraft:air";
              if (!actualItem.equals(expectedItem)) throw new IllegalStateException("unarmed scene expected non-air item: "+expectedItem);
          } else {
              var presentationStack=com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.presentationStack(selection).orElse(null);
              if(presentationStack==null) { requestHandRefresh(); return; }
              actualItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(presentationStack.getItem()).toString();
              if(!actualItem.equals(expectedItem)) { requestHandRefresh(); return; }
       }
            if(!sceneAimReady(mc)) { admissionBlock="scene-aim-readiness"; return; }
            if(SCENES[sceneIndex].equals("motion_jug_hit")&&!jugAimReady(mc)) { admissionBlock="scene-aim-or-target-readiness"; return; }
           if(!MeleeAttackClient.canAttemptNow(controlled,epoch,selection)) { admissionBlock="can-attempt-now"; return; }
          var cam=mc.gameRenderer.getMainCamera();preInputObservation=new JsonObject();preInputObservation.addProperty("bodyPosition",controlled.position().toString());preInputObservation.addProperty("eye",controlled.getEyePosition().toString());preInputObservation.addProperty("look",controlled.getLookAngle().toString());preInputObservation.addProperty("cameraEye",cam.getPosition().toString());preInputObservation.addProperty("cameraLook",cam.getLookVector().toString());preInputObservation.addProperty("selectedKind",selection.kind().name());preInputObservation.addProperty("selectedProfile",selection.itemToken().orElse(selection.kind().name()));preInputObservation.addProperty("actualItem",actualItem);preInputObservation.addProperty("expectedItem",expectedItem);
          long before=MeleeAttackClient.policy().sequence(); watermark=MeleeArcClient.diagnosticSnapshot().frameId();
             if(requiresFlight()) validateFlightMetadata(scene,null,SCENES[sceneIndex]);
             var mode=sceneMode(SCENES[sceneIndex]); if(mode!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT) post(GLFW.GLFW_MOUSE_BUTTON_2); else post(GLFW.GLFW_MOUSE_BUTTON_1);
          sequence=MeleeAttackClient.policy().sequence(); if(sequence<=before||!MeleeAttackClient.policy().pending())throw new IllegalStateException("production input not admitted"); admissionBlock="admitted";
            var req=MeleeAttackClient.diagnosticLastRequest().orElse(null); var expectedMode=scene.has("mode")?com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.valueOf(scene.get("mode").getAsString()):sceneMode(SCENES[sceneIndex]); if(expectedMode!=sceneMode(SCENES[sceneIndex]))throw new IllegalStateException("scene mode contract mismatch: "+scene);
            var admittedMode=expectedMode;
           if(req==null||req.attackMode()!=admittedMode)throw new IllegalStateException("unexpected diagnostic attack mode: "+req);
           if(disarmScene()){var peer=mc.level.getEntity(scene.get("peerBodyId").getAsInt());if(!(peer instanceof net.minecraft.world.entity.LivingEntity)||!peer.getUUID().toString().equals(scene.get("peerBodyUUID").getAsString())||!peer.getUUID().equals(req.targetUUID()))throw new IllegalStateException("disarm request did not nominate registered scene peer: "+req);scene.add("typedNomination",new com.google.gson.Gson().toJsonTree(req.typedTarget()));}
           if(SCENES[sceneIndex].equals("motion_jug_hit")){var jug=jugCaptureTarget(mc,scene);if(!jug.getUUID().equals(req.targetUUID()))throw new IllegalStateException("jug request did not nominate registered scene item: "+req);}
          if(SCENES[sceneIndex].equals("motion_wall_clip") || SCENES[sceneIndex].equals("motion_crowbar_wall_wide")) {
             var nomination=req.typedTarget();
              if(req.attackMode()!=sceneMode(SCENES[sceneIndex])
                     || selection.kind()!=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.valueOf(scene.get("expectedKind").getAsString())
                     || !(nomination instanceof com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block block)
                     || block.pos().getX()!=scene.get("targetBlockX").getAsInt()
                     || block.pos().getY()!=scene.get("targetBlockY").getAsInt()
                     || block.pos().getZ()!=scene.get("targetBlockZ").getAsInt()
                     || block.expectedBlockStateID()!=scene.get("targetBlockStateId").getAsInt())
                 throw new IllegalStateException("wall strike request did not nominate scene block: "+req);
               if(SCENES[sceneIndex].equals("motion_crowbar_wall_wide")) {
                   var nominations=req.nominations();
                   var matching=nominations.stream().filter(n -> n instanceof com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block blockNom
                           && blockNom.pos().getX()==scene.get("targetBlockX").getAsInt()
                           && blockNom.pos().getY()==scene.get("targetBlockY").getAsInt()
                           && blockNom.pos().getZ()==scene.get("targetBlockZ").getAsInt()
                           && blockNom.expectedBlockStateID()==scene.get("targetBlockStateId").getAsInt()).findFirst().orElse(null);
                   if(req.attackMode()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE
                           || selection.kind()!=com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD
                           || matching==null) throw new IllegalStateException("wide crowbar strike request did not nominate scene block: "+req);
                   scene.add("typedNominations",new com.google.gson.Gson().toJsonTree(nominations));
               } else scene.add("typedNomination",new com.google.gson.Gson().toJsonTree(nomination));
          }
         attackSent=armed=true; stage="capture_three";
     }
       static boolean prepareBeforeAdmission(Runnable prepare, BooleanSupplier admission) {
          prepare.run();
          return admission.getAsBoolean();
       }
       static String combatAdmissionReason(boolean player, boolean level, boolean connection, boolean spectator,
                                           UUID ownedBody, long committedEpoch, UUID policyBody, long policyEpoch, boolean pending) {
           if (!player || !level || !connection) return "missing-client-context";
           if (!spectator) return "not-spectator-carrier";
           if (ownedBody == null) return "missing-owned-body";
           if (committedEpoch <= 0) return "missing-committed-epoch";
           if (!ownedBody.equals(policyBody) || committedEpoch != policyEpoch) return "policy-context-mismatch";
           if (pending) return "request-pending";
           return "request-toggle-eligible";
       }
       private static void recordCombatReadiness(Minecraft mc, net.minecraft.world.entity.Mob controlled, long epoch,
                                                 boolean attempted, Boolean returned, boolean wasPending) {
           var policy=com.juicyslew.moonstation14.ms14.combat.CombatModeClient.policy();
           var p=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
           UUID owned=p==null?null:p.getUUID();
           String reason=combatAdmissionReason(mc.player!=null,mc.level!=null,mc.getConnection()!=null,
                   mc.player!=null&&mc.player.isSpectator(),owned,
                   com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch(),
                   policy.body(),policy.epoch(),wasPending);
           JsonObject s=new JsonObject(); s.addProperty("playerPresent",mc.player!=null); s.addProperty("levelPresent",mc.level!=null);
           s.addProperty("connectionPresent",mc.getConnection()!=null); s.addProperty("spectator",mc.player!=null&&mc.player.isSpectator());
           s.addProperty("ownedBody",owned==null?"missing":owned.toString()); s.addProperty("ownedEntityId",p==null?-1:p.getId());
           s.addProperty("committedEpoch",com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedCharacterEpoch());
           s.addProperty("attackBody",controlled==null?"missing":controlled.getUUID().toString()); s.addProperty("attackEpoch",epoch);
           s.addProperty("policyBody",policy.body()==null?"missing":policy.body().toString()); s.addProperty("policyEpoch",policy.epoch());
           s.addProperty("policySequence",policy.sequence()); s.addProperty("policyMode",policy.mode()); s.addProperty("policyPendingBefore",wasPending);
           s.addProperty("toggleAttempted",attempted); if(returned!=null)s.addProperty("toggleReturned",returned);
           s.addProperty("reason",reason); s.addProperty("policyPendingAfter",policy.pending());
           if(!s.equals(combatReadinessSnapshot)) { combatReadinessSnapshot=s; if(combatReadinessHistory.size()==16)combatReadinessHistory.remove(0); combatReadinessHistory.add(s.deepCopy()); }
           if(preInputObservation==null) preInputObservation=new JsonObject();
           preInputObservation.add("combatReadiness",s.deepCopy());
       }
      static com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode sceneMode(String name) {
          if(name.equals("motion_disarm_first")||name.equals("motion_disarm_flight")||name.equals("motion_observer_disarm_flight")) return com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.DISARM;
          if(isWideScene(name)) return com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE;
          return com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT;
      }
      private static void requestHandRefresh() {
         long now=System.nanoTime();
         if(now-lastRefresh>1_000_000_000L){com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();lastRefresh=now;}
     }
    private static void post(int button) { var p=new InputEvent.MouseButton.Pre(button,GLFW.GLFW_PRESS,0); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(p); net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Pre(button,GLFW.GLFW_RELEASE,0)); }
    private static void checkReply() throws Exception {
        if(!attackSent||replyReady)return;
        if(observer()) { Path p=dir.resolve("scene.json"); if(!Files.isRegularFile(p))return; JsonObject r=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if(!observerReplyMatches(scene,r,SCENES[sceneIndex]))return;
               if(!r.has("replyStatus")||!r.has("replySequence"))return;
               if(requiresFlight()? !"DISARM_HANDLED".equals(r.get("replyStatus").getAsString()): !"MISS".equals(r.get("replyStatus").getAsString()))throw new IllegalStateException("unexpected observer reply "+r);
              if(!r.has("replyServerTick")||!r.has("cooldownRemainingTicks"))throw new IllegalStateException("observer reply missing replyServerTick or cooldownRemainingTicks: "+r);
              long replyServerTick=r.get("replyServerTick").getAsLong(), cooldownRemainingTicks=r.get("cooldownRemainingTicks").getAsLong();
              if(replyServerTick<0||cooldownRemainingTicks<0)throw new IllegalStateException("observer reply has negative replyServerTick or cooldownRemainingTicks: "+r);
              warmupReadyAt=replyServerTick+cooldownRemainingTicks; serverReply=r;
              replyReady=true; if(!"wait_png".equals(stage))stage="capture_windows";
          } else { var r=MeleeAttackClient.diagnosticLastReply().orElse(null); if(r==null||!body.equals(r.bodyUUID())||r.epoch()!=scene.get("primaryEpoch").getAsLong()||r.sequence()!=sequence)return; if(disarmScene()){if((requiresFlight()&&r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_HANDLED)||(!requiresFlight()&&r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_FAILED&&r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_HANDLED))throw new IllegalStateException("unexpected disarm reply "+r.status());}else if(r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS&&r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT)throw new IllegalStateException("unexpected reply "+r.status()); if((SCENES[sceneIndex].equals("motion_wall_clip")||SCENES[sceneIndex].equals("motion_jug_hit")||SCENES[sceneIndex].equals("motion_crowbar_wall_wide"))&&r.status()!=com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT)throw new IllegalStateException(SCENES[sceneIndex]+" expected HIT, got "+r.status()); serverReply=new JsonObject();serverReply.addProperty("replyStatus",r.status().name());serverReply.addProperty("replySequence",r.sequence());replyReady=true; }
         for(JsonObject record:CAPTURES[sceneIndex])if(record!=null){record.add("reply",serverReply.deepCopy());record.addProperty("replyStatus",serverReply.get("replyStatus").getAsString());record.addProperty("authoritativeSequence",serverReply.get("replySequence").getAsLong());}
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
         if(!ENABLED||done)return;
         long now=System.nanoTime();
         if(lastRenderNanos!=0){lastRenderIntervalNanos=now-lastRenderNanos;if(lastRenderIntervalNanos<=MAX_STABLE_INTERVAL_NANOS)stableRenderIntervals++;else stableRenderIntervals=0;}
         lastRenderNanos=now;
          if(warming || !armed||scene==null||sampleIndex>=3)return;
         try { var snap=MeleeArcClient.diagnosticSnapshot(); boolean obs=observer(); boolean first=!observer()&&!third();
               double windowStart=windowStart(SCENES[sceneIndex],sampleIndex), windowEnd=windowEnd(SCENES[sceneIndex],sampleIndex); JsonObject observation=new JsonObject();observation.addProperty("expectedBody",String.valueOf(body));observation.addProperty("expectedSequence",sequence);observation.addProperty("expectedFirstPerson",first);observation.addProperty("expectedScene",SCENES[sceneIndex]);observation.addProperty("window",PHASES[sampleIndex]);observation.addProperty("windowStart",windowStart);observation.addProperty("windowEnd",windowEnd);observation.addProperty("frameId",snap.frameId());observation.addProperty("queuedCount",snap.queuedCount());observation.addProperty("profileCount",snap.profileCount());
            var observed=snap.rendered().stream().filter(a->a.strike()!=null).toList();JsonArray arcs=new JsonArray();for(var a:observed.stream().limit(8).toList()){JsonObject j=new JsonObject();j.addProperty("body",String.valueOf(a.body()));j.addProperty("sequence",a.requestSequence());j.addProperty("predicted",a.predicted());j.addProperty("firstPerson",a.firstPerson());var s=a.strike();j.addProperty("progress",s.progress());j.addProperty("extension",s.extension());j.addProperty("primaryEffectVertexCount",s.primaryEffectVertexCount());j.addProperty("trailGuideVertexCount",s.trailGuideVertexCount());arcs.add(j);if(body.equals(a.body())&&(obs?!a.predicted():a.predicted()&&a.requestSequence()==sequence)&&a.firstPerson()==first){if(firstObservedProgress==null)firstObservedProgress=s.progress();matchingProgress=s.progress();if(s.progress()>=windowStart(SCENES[sceneIndex],sampleIndex)&&s.progress()<=windowEnd(SCENES[sceneIndex],sampleIndex))matchingWindowObserved=true;}}observation.add("actualDiagnosticArcFrames",arcs);if(frameHistory.size()<64)frameHistory.add(observation);
                if(SCENES[sceneIndex].equals("motion_jug_hit")&&sampleIndex==0&&com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.redFlashProgress(jugCaptureTarget(mc(),scene))<=0)return;
              var matching=snap.rendered().stream().filter(a->a.body().equals(body)&&a.firstPerson()==first&&(obs?!a.predicted():a.predicted()&&a.requestSequence()==sequence)&&a.strike()!=null&&a.strike().primaryEffectVertexCount()>0).findFirst().orElse(null);
              if(!warming && matching!=null && missedWindow(matching.strike().progress(),SCENES[sceneIndex],sampleIndex)) throw new IllegalStateException("capture-window-missed expected="+windowStart+".."+windowEnd+" actual="+matching.strike().progress()+" peerSequence="+(scene.has("peerExpectedSwingSequence")?scene.get("peerExpectedSwingSequence").getAsLong():-1)+" replySequence="+(serverReply!=null&&serverReply.has("replySequence")?serverReply.get("replySequence").getAsLong():-1));
              var arc=matching!=null&&matching.strike().progress()>=windowStart&&matching.strike().progress()<=windowEnd?matching:null;
             if(snap.frameId()<=watermark||arc==null)return;
             if(requiresFlight()&&!flightEvidenceReady(now))return;
            int slot=sampleIndex++; watermark=snap.frameId(); final int boundScene=sceneIndex; final JsonObject boundSceneData=scene.deepCopy(); final String output=SCENES[boundScene]+"_"+PHASES[slot]+".png"; final var boundArc=arc; final var boundSnap=snap; stage="wait_png"; publish(mc(),boundScene,output,boundSceneData,boundArc,boundSnap,slot);
        } catch(Throwable t){fail(t);}
    }
     private static Minecraft mc(){return Minecraft.getInstance();}
      private static net.minecraft.world.entity.item.ItemEntity jugCaptureTarget(Minecraft mc,JsonObject capturedScene){if(!capturedScene.has("targetEntityId")||!capturedScene.has("targetEntityUUID"))throw new IllegalStateException("jug scene missing target entity metadata");UUID expected=UUID.fromString(capturedScene.get("targetEntityUUID").getAsString());var target=mc.level.getEntity(capturedScene.get("targetEntityId").getAsInt());if(target==null)return null;if(!(target instanceof net.minecraft.world.entity.item.ItemEntity item)||!item.getUUID().equals(expected))throw new IllegalStateException("jug scene item UUID/type mismatch");return item;}
        private static void publish(Minecraft mc,int boundScene,String output,JsonObject capturedScene,MeleeArcClient.DiagnosticArcFrame arc,MeleeArcClient.DiagnosticSnapshot snap,int slot){
            MeleeCaptureFocusPolicy.require(mc);
           final String sceneName=SCENES[boundScene]; final UUID capturedBody=body; final long visualSequence=arc.requestSequence();
          final JsonObject boundWarmupReply=warmupReply==null?new JsonObject():warmupReply.deepCopy();
          final long boundWarmupReadyAt=warmupReadyAt;
          final int boundStableRenderIntervals=stableRenderIntervals;
          var entity=mc.level.getEntity(capturedScene.get(observer()?"peerBodyId":"primaryBodyId").getAsInt());
           var camera=mc.gameRenderer.getMainCamera(); JsonObject r=new JsonObject();if(sceneName.equals("motion_jug_hit")){var jug=jugCaptureTarget(mc,capturedScene);r.addProperty("targetEntityId",jug.getId());r.addProperty("targetEntityUUID",jug.getUUID().toString());r.addProperty("targetRedFlashProgress",com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.redFlashProgress(jug));}r.addProperty("phase",output.substring(0,output.length()-4));r.addProperty("scene",sceneName);r.addProperty("sample",slot);r.addProperty("progress",arc.strike().progress());r.addProperty("body",capturedBody.toString());r.addProperty("sequence",visualSequence);r.addProperty("predicted",arc.predicted());r.addProperty("firstPerson",arc.firstPerson());r.addProperty("frame",snap.frameId());r.addProperty("renderFrame",snap.frameId());r.addProperty("texture",arc.texture().toString());r.addProperty("png",output);r.add("arc",new com.google.gson.Gson().toJsonTree(arc));r.addProperty("requestTimestamp",Instant.now().toString());r.add("serverScene",capturedScene);r.add("warmupReply",boundWarmupReply);r.addProperty("warmupReadyAtServerTick",boundWarmupReadyAt);r.addProperty("warmupStableRenderIntervals",boundStableRenderIntervals);r.addProperty("warmupRequiredStableRenderIntervals",REQUIRED_STABLE_INTERVALS);r.addProperty("sceneBody",capturedScene.get("bodyX").getAsDouble()+","+capturedScene.get("bodyY").getAsDouble()+","+capturedScene.get("bodyZ").getAsDouble());r.addProperty("scenePitch",capturedScene.has("pitch")?capturedScene.get("pitch").getAsDouble():0);r.addProperty("cameraEye",camera.getPosition().toString());r.addProperty("cameraLook",camera.getLookVector().toString());r.addProperty("hudHidden",mc.options.hideGui);if(requiresFlightScene(sceneName)){r.add("flightCue",flightObservation.deepCopy());r.add("committedFlight",capturedScene.getAsJsonObject("committedFlight").deepCopy());}if(preInputObservation!=null){r.addProperty("actualItem",preInputObservation.get("actualItem").getAsString());r.addProperty("expectedItem",preInputObservation.get("expectedItem").getAsString());}if(entity!=null){r.addProperty("bodyX",entity.getX());r.addProperty("bodyY",entity.getY());r.addProperty("bodyZ",entity.getZ());r.addProperty("bodyYaw",entity.getYRot());r.addProperty("bodyPitch",entity.getXRot());}
          MeleeDiagnosticScreenshotPublisher.publish(mc,dir.resolve(output),result->{r.addProperty("sha256",result.sha256());r.addProperty("requestTimestamp",result.requestedAt().toString());r.addProperty("capturedAt",result.completedAt().toString());CAPTURES[boundScene][slot]=r;callbacks++;
             if(serverReply!=null){r.add("reply",serverReply.deepCopy());r.addProperty("replyStatus",serverReply.get("replyStatus").getAsString());r.addProperty("authoritativeSequence",serverReply.get("replySequence").getAsLong());}
         },MeleeStrikePresentationClientDiagnostic::fail);
    }
       private static void advance(Minecraft mc) throws Exception {if(CAPTURES[sceneIndex].length!=3)throw new IllegalStateException("capture trio missing");long last=-1;for(JsonObject r:CAPTURES[sceneIndex]){if(r==null||!body.toString().equals(r.get("body").getAsString())||r.get("sequence").getAsLong()!=(observer()?0:sequence)||r.get("renderFrame").getAsLong()<=last)throw new IllegalStateException("invalid capture trio scene="+SCENES[sceneIndex]);last=r.get("renderFrame").getAsLong();}if(serverReply!=null)for(JsonObject r:CAPTURES[sceneIndex])if(!r.has("authoritativeSequence")||r.get("authoritativeSequence").getAsLong()!=serverReply.get("replySequence").getAsLong())throw new IllegalStateException("capture reply correlation missing");for(JsonObject r:CAPTURES[sceneIndex])records.add(r);scene=null;attackSent=false;replyReady=false;armed=false;sampleIndex=callbacks=0;serverReply=null;sceneIndex++;ticks=0;if(sceneIndex==SCENES.length)complete();else next(mc);}
        private static void complete() throws Exception {int expectedCount=SCENES.length*PHASES.length;if(records.size()!=expectedCount)throw new IllegalStateException("expected "+expectedCount+" captures, got "+records.size());JsonObject m=new JsonObject();m.addProperty("protocol",1);m.addProperty("status","COMPLETE");m.addProperty("phaseCount",records.size());m.addProperty("captureHudHidden",records.stream().allMatch(record->record.has("hudHidden")&&record.get("hudHidden").getAsBoolean()));m.addProperty("hudPolicy","flight-visible-others-hidden");m.add("phases",new com.google.gson.Gson().toJsonTree(records));Path manifest=dir.resolve("manifest.json"),manifestTmp=dir.resolve("manifest.json.tmp");Files.writeString(manifestTmp,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(m));Files.move(manifestTmp,manifest,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);JsonObject completion=new JsonObject();completion.addProperty("status","COMPLETE");completion.addProperty("phaseCount",records.size());Path out=dir.resolve("melee-capture-complete.json"),tmp=dir.resolve("melee-capture-complete.json.tmp");Files.writeString(tmp,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(completion));Files.move(tmp,out,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);System.out.println("MS14_MELEE_CAPTURE_COMPLETE phaseCount="+records.size()+" manifest="+manifest);done=true;restoreHud();}
      private static void restoreHud(){if(originalHideGui!=null){Minecraft.getInstance().options.hideGui=originalHideGui;originalHideGui=null;}}
      private static void fail(Throwable t){if(done)return;done=true;Minecraft mc=Minecraft.getInstance();try{if(mc.mouseHandler.isMouseGrabbed())mc.mouseHandler.releaseMouse();}catch(Throwable ignored){}restoreHud();try{if(dir!=null){JsonObject m=new JsonObject();m.addProperty("windowActive",mc.isWindowActive());m.addProperty("screenClass",mc.screen==null?"none":mc.screen.getClass().getName());m.addProperty("mouseGrabbed",mc.mouseHandler.isMouseGrabbed());m.addProperty("focusReason",MeleeCaptureFocusPolicy.reason(mc));m.addProperty("status","FAILED");m.add("flightReadiness",flightObservation.deepCopy());m.add("combatReadiness",combatReadinessSnapshot.deepCopy());m.add("combatReadinessHistory",new com.google.gson.Gson().toJsonTree(combatReadinessHistory));m.addProperty("reason",t.toString());m.addProperty("stage",stage);m.addProperty("scene",sceneIndex<SCENES.length?SCENES[sceneIndex]:"complete");m.addProperty("warming",warming);m.addProperty("admissionBlock",admissionBlock);m.addProperty("lastRenderIntervalNanos",lastRenderIntervalNanos);m.addProperty("stableRenderIntervals",stableRenderIntervals);m.addProperty("requiredStableRenderIntervals",REQUIRED_STABLE_INTERVALS);m.addProperty("warmupSequence",warmupReply!=null&&warmupReply.has("replySequence")?warmupReply.get("replySequence").getAsLong():-1);m.addProperty("warmupReadyAtServerTick",warmupReadyAt);m.addProperty("window",sampleIndex<PHASES.length?PHASES[sampleIndex]:"none");m.addProperty("sampleIndex",sampleIndex);m.addProperty("callbackCount",callbacks);m.add("frameHistory",new com.google.gson.Gson().toJsonTree(frameHistory));m.add("preInput",preInputObservation==null?new JsonObject():preInputObservation.deepCopy());m.add("jugReadiness",jugReadinessSnapshot.deepCopy());if(firstObservedProgress!=null)m.addProperty("firstObservedProgress",firstObservedProgress);if(matchingProgress!=null)m.addProperty("latestMatchingProgress",matchingProgress);m.addProperty("matchingProgressEnteredWindow",matchingWindowObserved);var req=MeleeAttackClient.diagnosticLastRequest().orElse(null);JsonObject request=new JsonObject();request.addProperty("present",req!=null);request.addProperty("epoch",req==null?-1:req.epoch());request.addProperty("sequence",req==null?-1:req.sequence());request.addProperty("mode",req==null?"missing":req.attackMode().name());request.addProperty("kind",req==null?"missing":req.expectedKind().name());m.add("lastRequest",request);var reply=MeleeAttackClient.diagnosticLastReply().orElse(null);JsonObject response=new JsonObject();response.addProperty("present",reply!=null);response.addProperty("body",reply==null?"missing":String.valueOf(reply.bodyUUID()));response.addProperty("sequence",reply==null?-1:reply.sequence());response.addProperty("status",reply==null?"missing":reply.status().name());response.addProperty("correlated",reply!=null&&body!=null&&body.equals(reply.bodyUUID())&&reply.sequence()==sequence);m.add("lastReply",response);var catalog=com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog();JsonObject leaves=new JsonObject();for(String leaf:new String[]{"weapon_arc_fist","weapon_arc_thrust","weapon_arc_slash"}){var profile=catalog.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",leaf));leaves.addProperty(leaf,profile!=null&&profile.strike()!=null);}m.add("catalogLeafStrikeEnabled",leaves);JsonObject presence=new JsonObject();for(String leaf:new String[]{"weapon_arc_fist","weapon_arc_thrust","weapon_arc_slash"})presence.addProperty(leaf,catalog.contains(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14",leaf)));m.add("catalogLeafProfilesPresent",presence);Files.writeString(dir.resolve("melee-capture-failed.json"),m.toString());}}catch(Exception ignored){}System.err.println("MS14_MELEE_STRIKE_CAPTURE_FAILED "+t);}
}
