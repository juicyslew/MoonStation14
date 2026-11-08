package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcTimeline;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
import com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/** Short-lived, client-only presentation prediction; never participates in melee authority. */
@EventBusSubscriber(modid=MoonStation14.MOD_ID, value=Dist.CLIENT)
public final class MeleeArcClient {
    private static final List<Arc> ARCS = new ArrayList<>();
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("ms14.meleePresentationDiagnostic");
    private static long diagnosticFrameId, diagnosticLocalQueued, diagnosticObserverQueued;
    private static List<DiagnosticArcFrame> diagnosticRendered = List.of();
    private static List<DiagnosticArcFrame> diagnosticFrameWork = List.of();
    public record DiagnosticStrikeFrame(double progress, double extension, int primaryEffectVertexCount,
                                         int trailGuideVertexCount, Vec3 actualOrigin, Vec3 forward, Vec3 endpoint,
                                        double range, String mode, float facingYaw, List<Vec3> boundaryEndpoints) { }
    public record DiagnosticArcFrame(UUID body, long requestSequence, boolean predicted, boolean firstPerson,
                                     ResourceLocation texture, double alpha, DiagnosticStrikeFrame strike) {
        public DiagnosticArcFrame(UUID body, long requestSequence, boolean predicted, boolean firstPerson,
                                  ResourceLocation texture, double alpha) {
            this(body, requestSequence, predicted, firstPerson, texture, alpha, null);
        }
    }
    public record DiagnosticSnapshot(long frameId, List<DiagnosticArcFrame> rendered, long localQueued,
                                     long observerQueued, int profileCount, int queuedCount) { }
    /** Immutable observation of production queue/render activity; never changes queue state. */
    public static DiagnosticSnapshot diagnosticSnapshot() {
        if (!DIAGNOSTIC) return new DiagnosticSnapshot(0, List.of(), 0, 0, 0, 0);
        return new DiagnosticSnapshot(diagnosticFrameId, List.copyOf(diagnosticRendered), diagnosticLocalQueued,
                diagnosticObserverQueued, com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog().size(), ARCS.size());
    }
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static final class RenderBuffers {
        private static final ByteBufferBuilder ARC_BUFFER = new ByteBufferBuilder(786432);
        private static final MultiBufferSource.BufferSource ARC_BUFFERS = MultiBufferSource.immediate(ARC_BUFFER);
    }
    record FirstPersonFrame(Vec3 origin, Vec3 look, Vec3 right, Vec3 up, org.joml.Quaternionf rotation) { }
    record RenderProjection(Vec3 position, float scale) { }
    private MeleeArcClient() { }
    record Arc(UUID body, int entityId, long epoch, long requestSequence, ResourceLocation dimension, Vec3 point, Vec3 direction, float rotation, long start,
                        MeleeArcProfile profile, double rate, double angle, double offset, ResourceLocation texture, net.minecraft.world.item.ItemStack copied, boolean swingLeft,
                        MeleeArcGeometry.StrikeCapture strikeGeometry, String handId) { }
    public record StrikeView(UUID body, long requestSequence, boolean predicted,
                              MeleeArcGeometry.StrikeCapture geometry, com.juicyslew.moonstation14.ms14.combat.MeleeStrikeProfile profile,
                              com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline.Sample sample,
                              boolean swingLeft) { }

    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, long tick, long epoch) {
        enqueue(body,selection,target,tick,epoch,com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT);
    }
    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, long tick, long epoch, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode) {
        enqueue(body,selection,target,tick,epoch,mode,0);
    }
    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, long tick, long epoch, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode, long requestSequence) {
        enqueue(body, selection, target, null, tick, epoch, mode, requestSequence);
    }
    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block block,
                               long tick, long epoch, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode, long requestSequence) {
        if (body == null || selection == null) return;
        var data = selection.weapon();
        var profile = com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog().get(mode==com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE?data.wideArc():data.arc());
        if (profile == null) return;
        net.minecraft.world.entity.Entity nominated = null;
        if (target != null && body.level() instanceof net.minecraft.world.level.Level level) {
            nominated = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    body.getBoundingBox().inflate(data.range() + body.getBbWidth() + 2), e -> target.equals(e.getUUID())).stream().findFirst().orElse(null);
        }
        var aim = MeleeArcGeometry.capture(body.position(), body.getBbHeight(), body.getLookAngle(),
                nominated == null ? null : nominated.position(), data.range());
        var strike = MeleeArcGeometry.captureStrike(body, data, mode, nominated, block);
        aim = MeleeArcGeometry.legacyAimWithStrikeFallback(aim, strike);
        if (aim == null) return;
        Vec3 direction = aim.direction();
        Vec3 point = aim.attackPoint();
        ResourceLocation texture = MeleeDisarmPresentationPolicy.texture(profile, mode);
        net.minecraft.world.item.ItemStack copied = presentation(selection);
        if (selection.kind() != MeleeWeaponResolver.Kind.UNARMED && copied == null) return;
        boolean oldLeft = sourceSwingLeft(body, selection, copied);
        ARCS.add(new Arc(body.getUUID(), body.getId(), epoch, requestSequence, body.level().dimension().location(), point, direction, data.wideAnimationRotation(), tick, profile,
                data.attackRate(), data.angleDegrees(), data.animationOffset(), texture, copied, predictedSwingLeft(oldLeft), strike, selection.activeHand()));
        if (DIAGNOSTIC) diagnosticLocalQueued++;
    }

    /** Appends the server-frozen observer presentation to the same renderer queue. */
    public static boolean receiveObserver(com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload payload) {
        var mc = Minecraft.getInstance();
        if (payload == null || mc.player == null || mc.level == null
                || !observerPayloadEligible(payload, mc.player.getUUID(), mc.level.dimension().location())) return false;
        var actor = mc.level.getEntity(payload.bodyEntityId());
        if (!(actor instanceof Mob mob) || mob.isRemoved() || !payload.bodyUUID().equals(mob.getUUID())) return false;
        var profile = com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog().get(payload.arc());
        if (profile == null) return false;
        var copied = payload.copiedWeapon() == null ? null : payload.copiedWeapon().stack().copy();
        ARCS.add(new Arc(payload.bodyUUID(), payload.bodyEntityId(), 0, 0, payload.dimension(), payload.attackPoint(),
                payload.direction(), payload.wideAnimationRotation(), mc.level.getGameTime(), profile,
                payload.attackRate(), payload.angleDegrees(), payload.animationOffset(), MeleeDisarmPresentationPolicy.texture(profile, payload.strikeGeometry() == null ? null : payload.strikeGeometry().mode()), copied, payload.swingLeft(), payload.strikeGeometry(), payload.activeHand()));
        if (DIAGNOSTIC) diagnosticObserverQueued++;
        return true;
    }

    public static boolean observerPayloadEligible(com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload p, UUID player, ResourceLocation dimension) {
        return p != null && player != null && dimension != null && (p.initiatingPlayerUUID() == null || !p.initiatingPlayerUUID().equals(player))
                && p.dimension().equals(dimension);
    }

    private static net.minecraft.world.item.ItemStack presentation(MeleeWeaponResolver.Selection selected) {
        return com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.presentationStack(selected).orElse(null);
    }

    static boolean sourceSwingLeft(Mob body, MeleeWeaponResolver.Selection selected, net.minecraft.world.item.ItemStack copied) {
        if (selected.kind() == MeleeWeaponResolver.Kind.UNARMED) {
            var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
            return state != null && state.data().swingLeft();
        }
        var state = copied == null ? null : copied.get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
        return state != null && state.swingLeft();
    }
    static boolean predictedSwingLeft(boolean current) { return !current; }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        reconcileWorld(mc.level);
        if (mc.level == null) return;
        var buffers = RenderBuffers.ARC_BUFFERS; var camera = event.getCamera().getPosition();
        Iterator<Arc> it = ARCS.iterator();
        while (it.hasNext()) {
            Arc arc = it.next();
            if (!arc.dimension.equals(mc.level.dimension().location())) { it.remove(); continue; }
            double elapsed = timelineElapsed(mc.level.getGameTime(), arc.start, event.getPartialTick().getGameTimeDeltaPartialTick(false));
            var sample = MeleeArcTimeline.sample(arc.profile, arc.rate, arc.offset, arc.angle, arc.swingLeft, Math.max(0, elapsed));
            var strikeSample = strikeSample(arc, elapsed);
            if (sample.expired() && (strikeSample == null || strikeSample.expired())) { it.remove(); continue; }
            var body = mc.level.getEntity(arc.entityId);
            if (!(body instanceof Mob mob) || body.isRemoved() || !arc.body.equals(body.getUUID())) { it.remove(); continue; }
            Vec3 pos = arc.point;
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Mob controlledBody = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient
                    .ownedCharacterForPresentation();
            boolean firstPersonOwner = firstPersonProjection(mob.getUUID(), controlledBody == null ? null : controlledBody.getUUID(),
                    mc.getCameraEntity() == mob, mc.options.getCameraType().isFirstPerson());
            if (strikeSample != null && arc.profile.strike() != null) {
                Optional<StrikeView> current = strikeFor(mob, partial);
                if (current.isPresent() && matchesStrike(arc, current.get())) {
                    StrikeView strike = current.get();
                    Vec3 origin = NeutralMeleeStrikeEffectGeometry.source(strike);
                    var presentation = MeleeDisarmPresentationPolicy.strikePresentation(arc.profile,
                            strike.geometry() == null ? null : strike.geometry().mode(), arc.copied != null);
                    var counts = presentation == MeleeDisarmPresentationPolicy.StrikePresentation.DISARM_GLYPH
                            ? renderDisarmGlyph(arc, strike, origin, event.getPoseStack(), buffers, camera)
                            : presentation == MeleeDisarmPresentationPolicy.StrikePresentation.TEXTURE_GLYPH
                            ? renderTextureGlyph(arc, strike, origin, event.getPoseStack(), buffers, camera)
                            : presentation == MeleeDisarmPresentationPolicy.StrikePresentation.UNARMED_TRAIL
                            ? MeleeStrikeTrailRenderer.renderCounts(origin, strike, event.getPoseStack(), buffers, camera)
                            : new MeleeStrikeTrailRenderer.RenderCounts(0, 0);
                    if (presentation == MeleeDisarmPresentationPolicy.StrikePresentation.WEAPON_ONLY) {
                        var looseProfile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(arc.copied, true);
                        if (looseProfile != null) {
                            Vec3 tip = NeutralMeleeStrikeEffectGeometry.weaponTipPoint(origin, strike);
                            var g = strike.geometry();
                            PoseStack weaponPose = event.getPoseStack();
                            weaponPose.pushPose();
                            try {
                                 Vec3 endpoint = NeutralMeleeStrikeEffectGeometry.selectedEndpoint(strike);
                                 var direction = NeutralMeleeStrikeEffectGeometry.capturedDirection(g, endpoint);
                                 var rotation = CapturedAttackFramePose.weaponRotation(g.fullDirection(), direction, g.facingYaw(),
                                        arc.profile.weaponPose(), g.mode(), arc.rotation);
                                var model = Minecraft.getInstance().getItemRenderer().getModel(arc.copied, mc.level, null, 0);
                                var appearance = com.juicyslew.moonstation14.ms14.botany.ProduceWorldAppearance.scale(arc.copied);
                                 float weaponScale = (float) arc.profile.weaponPose().scale();
                                   double clearance=firstPersonOwner
                                           ? NeutralMeleeStrikeEffectGeometry.rearClearance(g,direction)
                                           : NeutralMeleeStrikeEffectGeometry.observerClearance(g,direction,
                                           arc.direction.scale(MeleeArcTimeline.lunge(Math.max(0,elapsed))));
                                  if(!Double.isFinite(clearance)) clearance=0; // Legacy/test snapshots have no bounds.
                                  if (firstPersonOwner) {
                                       double physicalAvailable=endpoint.dot(direction.normalize())-clearance;
                                      clearance=CapturedAttackFramePose.ownerClearance(clearance,physicalAvailable,
                                              NeutralMeleeStrikeEffectGeometry.source(strike),camera,direction,
                                              cameraLook(event.getCamera().getLookVector()),.06);
                                      if(!Double.isFinite(clearance)) continue;
                                  }
                                   var fitted=firstPersonOwner ? CapturedAttackFramePose.constrainedWeapon(model,looseProfile,appearance,rotation,direction,tip,weaponScale,clearance) : null;
                                    if(firstPersonOwner && fitted==null) continue;
                                    Vec3 anchor=firstPersonOwner ? fitted.anchor() : CapturedAttackFramePose.weaponAnchor(model,looseProfile,appearance,rotation,direction,tip,weaponScale);
                                    if(anchor==null) continue;
                                    weaponPose.translate(anchor.x-camera.x, anchor.y-camera.y, anchor.z-camera.z);
                                    weaponPose.last().pose().mul(firstPersonOwner
                                            ? CapturedAttackFramePose.axialTransform(direction,fitted.axialScale(),rotation,weaponScale)
                                            : CapturedAttackFramePose.rigidWeaponTransform(rotation,weaponScale));
                                 int[] emitted={0};
                                 var clippedBuffers=firstPersonOwner ? buffers : new MeleeQuadBufferSource(buffers,
                                         direction.x,direction.y,direction.z,clearance-camera.dot(direction.normalize()),1.0e-5,emitted);
                                 int vertices = com.juicyslew.moonstation14.ms14.loose_objects.client.WorldItemPresentationRenderer.renderCameraFacingCounted(
                                         arc.copied, looseProfile, weaponPose, clippedBuffers, 15728880, mc.level, 0,
                                         strike.profile().trailAlpha() == 0 ? 0 : (float) Math.max(0, Math.min(1,
                                                 strike.sample().trailAlpha() / strike.profile().trailAlpha())));
                                 if(clippedBuffers instanceof MeleeQuadBufferSource clipped) { clipped.finish(); vertices=emitted[0]; }
                                 counts = new MeleeStrikeTrailRenderer.RenderCounts(vertices, 0);
                            } finally { weaponPose.popPose(); }
                        }
                    }
                    if (DIAGNOSTIC && counts.primaryVertices() > 0) {
                        var g=strike.geometry();
                        var df=new DiagnosticStrikeFrame(strike.sample().progress(),strike.sample().extension(),counts.primaryVertices(),counts.guideVertices(),
                                origin,g.fullDirection(),g.centerEndpoint(),g.range(),g.mode().name(),g.facingYaw(),g.boundaryEndpoints());
                        diagnosticFrameWork.add(new DiagnosticArcFrame(arc.body,arc.requestSequence,arc.epoch>0,firstPersonOwner,arc.texture,strike.sample().trailAlpha(),df));
                    }
                }
                continue;
            }
            if (sample.expired()) continue; // Legacy glyph is gone; retain only the still-live body strike.
            FirstPersonFrame firstPersonFrame = firstPersonOwner
                    ? firstPersonFrame(camera, cameraLook(event.getCamera().getLookVector()),
                    cameraLook(event.getCamera().getLeftVector()).scale(-1), cameraLook(event.getCamera().getUpVector()),
                    event.getCamera().rotation()) : null;
            if (firstPersonOwner) {
                // World-stage projection: keep the same queued arc/timeline, but put it
                // just in front of the camera instead of burying it in the player's body.
                pos = firstPersonFrame.origin();
            }
            if (!sample.expired() && arc.profile.animation() != MeleeArcProfile.Animation.NONE)
                pos = (firstPersonOwner ? pos : mob.getPosition(partial).add(0, mob.getBbHeight()*.5, 0))
                        .add(firstPersonOwner ? firstPersonFrame.look().scale(sample.offsetMultiplier()) : arc.direction.scale(sample.offsetMultiplier()));
            if (!sample.expired() && arc.profile.animation() == MeleeArcProfile.Animation.SLASH)
                pos = pos.add(firstPersonOwner
                        ? firstPersonFrame.right().scale(sample.lateralOffset())
                        : new Vec3(arc.direction.z, 0, -arc.direction.x).scale(sample.lateralOffset()));
            var projection = renderProjection(pos, camera, firstPersonOwner);
            pos = projection.position();
            if (arc.copied != null) {
                var looseProfile=com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(arc.copied,true);
                if(looseProfile==null) { it.remove(); continue; }
                PoseStack ip=event.getPoseStack(); ip.pushPose(); try { ip.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z); ip.scale(projection.scale(),projection.scale(),projection.scale());
                ip.mulPose(billboardRotation(event.getCamera().rotation(), arc.direction, arc.rotation,
                        (float) sample.rotationDelta()));
                com.juicyslew.moonstation14.ms14.loose_objects.client.WorldItemPresentationRenderer.renderCameraFacing(
                        arc.copied,looseProfile,ip,buffers,15728880,mc.level,0,(float)sample.alpha());
                if (DIAGNOSTIC && sample.alpha() > 0) diagnosticFrameWork.add(new DiagnosticArcFrame(arc.body, arc.requestSequence, arc.epoch > 0, firstPersonOwner, arc.texture, sample.alpha()));
                } finally { ip.popPose(); } continue;
            }
            PoseStack pose = event.getPoseStack(); pose.pushPose(); try { pose.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z); pose.scale(projection.scale(),projection.scale(),projection.scale());
            pose.mulPose(billboardRotation(event.getCamera().rotation(), arc.direction, arc.rotation,
                    (float) sample.rotationDelta()));
            float size = .5f;
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(arc.texture));
            var matrix = pose.last().pose(); int alpha = Math.round((float)(255*sample.alpha()));
            consumer.addVertex(matrix,-size,-size,0).setColor(255,255,255,alpha).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,-size,0).setColor(255,255,255,alpha).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,size,0).setColor(255,255,255,alpha).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,-size,size,0).setColor(255,255,255,alpha).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            if (DIAGNOSTIC && sample.alpha() > 0) diagnosticFrameWork.add(new DiagnosticArcFrame(arc.body, arc.requestSequence, arc.epoch > 0, firstPersonOwner, arc.texture, sample.alpha()));
            } finally { pose.popPose(); }
        }
        buffers.endBatch();
        if (DIAGNOSTIC) diagnosticRendered = List.copyOf(diagnosticFrameWork);
    }
    private static MeleeStrikeTrailRenderer.RenderCounts renderDisarmGlyph(Arc arc, StrikeView strike, Vec3 origin,
            PoseStack glyph, MultiBufferSource buffers, Vec3 camera) {
        Vec3 tip = NeutralMeleeStrikeEffectGeometry.weaponTipPoint(origin, strike);
        var g = strike.geometry(); glyph.pushPose();
        try {
            glyph.translate(tip.x-camera.x, tip.y-camera.y, tip.z-camera.z);
            float size = MeleeDisarmPresentationPolicy.glyphHalfExtent(strike.profile().trailWidth());
            var window = MeleeDisarmPresentationPolicy.atlasWindow(strike.sample().progress(), arc.profile.disarmFrames());
            var consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(arc.profile.disarmTexture()));
            int alpha = Math.round(255 * normalizedTrailAlpha(strike));
            glyph.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
                var matrix = glyph.last().pose();
                consumer.addVertex(matrix,-size,-size,0).setColor(255,255,255,alpha).setUv(window.u0(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
                consumer.addVertex(matrix,size,-size,0).setColor(255,255,255,alpha).setUv(window.u1(),1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
                consumer.addVertex(matrix,size,size,0).setColor(255,255,255,alpha).setUv(window.u1(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
                consumer.addVertex(matrix,-size,size,0).setColor(255,255,255,alpha).setUv(window.u0(),0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
        } finally { glyph.popPose(); }
        return new MeleeStrikeTrailRenderer.RenderCounts(4, 0);
    }
    private static MeleeStrikeTrailRenderer.RenderCounts renderTextureGlyph(Arc arc, StrikeView strike, Vec3 origin,
            PoseStack glyph, MultiBufferSource buffers, Vec3 camera) {
        Vec3 target = NeutralMeleeStrikeEffectGeometry.selectedEndpoint(strike);
        Vec3 point = target == null ? origin : target;
        float size = .5f;
        glyph.pushPose();
        try {
            glyph.translate(point.x-camera.x, point.y-camera.y, point.z-camera.z);
            glyph.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(arc.profile.texture()));
            var matrix = glyph.last().pose(); int alpha = Math.round(255 * MeleeDisarmPresentationPolicy.texturedGlyphAlpha(arc.profile, normalizedTrailAlpha(strike)));
            var uv = MeleeDisarmPresentationPolicy.atlasWindow(strike.sample().progress(), arc.profile.textureFrames(), arc.profile.textureColumns());
            consumer.addVertex(matrix,-size,-size,0).setColor(255,255,255,alpha).setUv(uv.u0(),uv.v1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,-size,0).setColor(255,255,255,alpha).setUv(uv.u1(),uv.v1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,size,0).setColor(255,255,255,alpha).setUv(uv.u1(),uv.v0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,-size,size,0).setColor(255,255,255,alpha).setUv(uv.u0(),uv.v0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
        } finally { glyph.popPose(); }
        return new MeleeStrikeTrailRenderer.RenderCounts(4, 0);
    }
    static float normalizedTrailAlpha(StrikeView strike) {
        if (strike == null || strike.profile().trailAlpha() <= 0) return 0;
        return (float)Math.max(0, Math.min(1, strike.sample().trailAlpha() / strike.profile().trailAlpha()));
    }
    @SubscribeEvent public static void beginDiagnosticFrame(RenderFrameEvent.Pre event) {
        if (DIAGNOSTIC) { diagnosticFrameId++; diagnosticFrameWork = new ArrayList<>(); }
    }
    static boolean firstPersonProjection(UUID renderedBody, UUID controlledBody, boolean cameraIsBody,
                                        boolean firstPersonCamera) {
        return renderedBody != null && renderedBody.equals(controlledBody) && cameraIsBody && firstPersonCamera;
    }
    static boolean cameraFacingWeapon(FirstPersonFrame frame) { return frame != null; }
    static RenderProjection renderProjection(Vec3 canonicalPosition, Vec3 camera, boolean firstPersonOwner) {
        if (!firstPersonOwner) return new RenderProjection(canonicalPosition, 1f);
        return new RenderProjection(camera.add(canonicalPosition.subtract(camera).scale(.1)), .1f);
    }
    static FirstPersonFrame firstPersonFrame(Vec3 camera, Vec3 rawLook, Vec3 rawRight, Vec3 rawUp,
                                             org.joml.Quaternionf cameraRotation) {
        var forward = new org.joml.Vector3f(0, 0, -1).rotate(cameraRotation);
        var localRight = new org.joml.Vector3f(1, 0, 0).rotate(cameraRotation);
        var localUp = new org.joml.Vector3f(0, 1, 0).rotate(cameraRotation);
        Vec3 look = new Vec3(forward.x, forward.y, forward.z).normalize();
        Vec3 right = new Vec3(localRight.x, localRight.y, localRight.z).normalize();
        Vec3 up = new Vec3(localUp.x, localUp.y, localUp.z).normalize();
        Vec3 origin = camera.add(look.scale(1.05)).add(right.scale(.24)).add(up.scale(-.22));
        return new FirstPersonFrame(origin, look, right, up, new org.joml.Quaternionf(cameraRotation));
    }
    static Vec3 cameraLook(org.joml.Vector3f look) { return new Vec3(look.x(), look.y(), look.z()); }
    public static void clear() { ARCS.clear(); }
    private static com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline.Sample strikeSample(Arc arc, double elapsed) {
        if (arc.profile == null || arc.profile.strike() == null || arc.strikeGeometry == null) return null;
        return com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline.sample(arc.profile.strike(), arc.rate, Math.max(0, elapsed));
    }
    /** Read-only view of the newest live strike for a body; does not alter queue state. */
    public static Optional<StrikeView> strikeFor(net.minecraft.world.entity.LivingEntity body, float partialTick) {
        if (body == null || body.isRemoved() || !Float.isFinite(partialTick) || body.level().isClientSide == false) return Optional.empty();
        return strikeFor(body.getUUID(), body.getId(), body.level().dimension().location(), body.level().getGameTime(), partialTick);
    }
    static boolean matchesStrike(Arc arc, StrikeView view) {
        return arc != null && view != null && arc.requestSequence == view.requestSequence()
                && arc.strikeGeometry != null && arc.strikeGeometry == view.geometry();
    }
    static Optional<StrikeView> strikeFor(UUID bodyId, int entityId, ResourceLocation dimension, long gameTime, float partialTick) {
        if (bodyId == null || dimension == null || !Float.isFinite(partialTick)) return Optional.empty();
        Arc latest = null;
        for (Arc arc : ARCS) if (arc.entityId == entityId && arc.body.equals(bodyId)
                && arc.dimension.equals(dimension) && arc.profile != null && arc.profile.strike() != null
                && arc.strikeGeometry != null && !com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline
                .sample(arc.profile.strike(), arc.rate, timelineElapsed(gameTime, arc.start, partialTick)).expired()
                && (latest == null || arc.start >= latest.start)) latest = arc;
        if (latest == null) return Optional.empty();
        double elapsed = timelineElapsed(gameTime, latest.start, partialTick);
        var sample = com.juicyslew.moonstation14.ms14.combat.MeleeStrikeTimeline.sample(latest.profile.strike(), latest.rate, elapsed);
        if (sample.expired()) return Optional.empty();
        return Optional.of(new StrikeView(latest.body, latest.requestSequence, latest.epoch > 0,
                latest.strikeGeometry, latest.profile.strike(), sample, latest.swingLeft));
    }
    public static void reconcileWorld(net.minecraft.client.multiplayer.ClientLevel level) {
        if (observedLevel != level) { ARCS.clear(); observedLevel = level; }
    }
    public static void clearPredicted() { ARCS.removeIf(a -> a.epoch > 0); }
    static double timelineElapsed(long gameTime, long start, float partialTick) { return Math.max(0, (gameTime - start + partialTick) / 20.0); }
    static boolean rejectedReply(MeleeAttackReply.Status status) {
        return status != null && switch (status) {
            case NO_AUTHORITY, WRONG_EPOCH, REPLAY, RATE_LIMITED, STALE_WEAPON, REJECTED,
                    PRE_ATTEMPT_CANCELLED, CONSUMED_CANCELLED -> true;
            case MISS, HIT, HANDLED, DISARM_FAILED, DISARM_HANDLED -> false;
        };
    }
    public static int cancelRejected(UUID body, long epoch, long sequence) {
        int before=ARCS.size(); ARCS.removeIf(a -> a.epoch > 0 && a.body.equals(body) && a.epoch == epoch && a.requestSequence == sequence);
        return before-ARCS.size();
    }
    static void appendForTest(Arc arc) { ARCS.add(arc); }
    static int queuedForTest() { return ARCS.size(); }
    static float rotationRadians(Vec3 direction, float weaponRotation, double timelineDelta) {
        return (float)Math.atan2(direction.x, direction.z) + (weaponRotation + (float)timelineDelta) * (float)Math.PI / 180;
    }
    /** A melee sprite always lies in the camera plane; rotate its in-plane axis by the projected attack vector. */
    static org.joml.Quaternionf billboardRotation(org.joml.Quaternionf cameraRotation, Vec3 direction,
                                                  float weaponRotation, float timelineDelta) {
        var right = new org.joml.Vector3f(1, 0, 0).rotate(cameraRotation);
        var up = new org.joml.Vector3f(0, 1, 0).rotate(cameraRotation);
        double x = direction.x * right.x + direction.y * right.y + direction.z * right.z;
        double y = direction.x * up.x + direction.y * up.y + direction.z * up.z;
        float angle = (float) Math.atan2(y, x) + (weaponRotation + timelineDelta) * (float) Math.PI / 180;
        return new org.joml.Quaternionf(cameraRotation).mul(Axis.ZP.rotation(angle));
    }
    public static boolean contextMatches(UUID arcBody,long arcEpoch,ResourceLocation arcDimension,UUID body,long epoch,ResourceLocation dimension) {
        return arcBody.equals(body) && arcEpoch == epoch && arcDimension.equals(dimension);
    }
    public static void reconcile(UUID body,long epoch,ResourceLocation dimension) { ARCS.removeIf(a->!a.dimension.equals(dimension) || (a.epoch > 0 && !contextMatches(a.body,a.epoch,a.dimension,body,epoch,dimension))); }

    /** Visual-only displacement for the most recently queued arc associated with this rendered body. */
    public static Vec3 lungeOffset(net.minecraft.world.entity.LivingEntity body, float partialTick) {
        Arc latest = null;
        for (Arc arc : ARCS) if (arc.entityId == body.getId() && arc.body.equals(body.getUUID())
                && arc.dimension.equals(body.level().dimension().location())
                && (latest == null || arc.start >= latest.start)) latest = arc;
        if (latest == null) return Vec3.ZERO;
        double elapsed = (body.level().getGameTime() - latest.start + partialTick) / 20.0;
        if (elapsed < 0 || elapsed > .10) return Vec3.ZERO;
        return latest.direction.scale(MeleeArcTimeline.lunge(elapsed));
    }
}
