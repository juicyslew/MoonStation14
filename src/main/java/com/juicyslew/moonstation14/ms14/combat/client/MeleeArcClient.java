package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcProfile;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcTimeline;
import com.juicyslew.moonstation14.ms14.combat.MeleeArcGeometry;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/** Short-lived, client-only presentation prediction; never participates in melee authority. */
@EventBusSubscriber(modid=MoonStation14.MOD_ID, value=Dist.CLIENT)
public final class MeleeArcClient {
    private static final List<Arc> ARCS = new ArrayList<>();
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static final ByteBufferBuilder ARC_BUFFER = new ByteBufferBuilder(786432);
    private static final MultiBufferSource.BufferSource ARC_BUFFERS = MultiBufferSource.immediate(ARC_BUFFER);
    private MeleeArcClient() { }
    private record Arc(UUID body, int entityId, long epoch, ResourceLocation dimension, Vec3 point, Vec3 direction, float rotation, long start,
                        MeleeArcProfile profile, double rate, double angle, double offset, ResourceLocation texture, net.minecraft.world.item.ItemStack copied, boolean swingLeft) { }

    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, long tick, long epoch) {
        enqueue(body,selection,target,tick,epoch,com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT);
    }
    public static void enqueue(Mob body, MeleeWeaponResolver.Selection selection, UUID target, long tick, long epoch, com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode mode) {
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
        if (aim == null) return;
        Vec3 direction = aim.direction();
        Vec3 point = aim.attackPoint();
        ResourceLocation texture = profile.texture();
        net.minecraft.world.item.ItemStack copied = presentation(selection);
        if (selection.kind() != MeleeWeaponResolver.Kind.UNARMED && copied == null) return;
        boolean oldLeft = sourceSwingLeft(body, selection, copied);
        ARCS.add(new Arc(body.getUUID(), body.getId(), epoch, body.level().dimension().location(), point, direction, body.getYRot(), tick, profile,
                data.attackRate(), data.angleDegrees(), data.animationOffset(), texture, copied, predictedSwingLeft(oldLeft)));
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
        ARCS.add(new Arc(payload.bodyUUID(), payload.bodyEntityId(), 0, payload.dimension(), payload.attackPoint(),
                payload.direction(), payload.wideAnimationRotation(), mc.level.getGameTime(), profile,
                payload.attackRate(), payload.angleDegrees(), payload.animationOffset(), profile.texture(), copied, payload.swingLeft()));
        return true;
    }

    public static boolean observerPayloadEligible(com.juicyslew.moonstation14.ms14.combat.MeleeArcPayload p, UUID player, ResourceLocation dimension) {
        return p != null && player != null && dimension != null && !p.initiatingPlayerUUID().equals(player)
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
        var buffers = ARC_BUFFERS; var camera = event.getCamera().getPosition();
        Iterator<Arc> it = ARCS.iterator();
        while (it.hasNext()) {
            Arc arc = it.next();
            if (!arc.dimension.equals(mc.level.dimension().location())) { it.remove(); continue; }
            double elapsed = (mc.level.getGameTime() - arc.start) / 20.0;
            var sample = MeleeArcTimeline.sample(arc.profile, arc.rate, arc.offset, arc.angle, arc.swingLeft, Math.max(0, elapsed));
            if (sample.expired()) { it.remove(); continue; }
            var body = mc.level.getEntity(arc.entityId);
            if (!(body instanceof Mob mob) || body.isRemoved() || !arc.body.equals(body.getUUID())) { it.remove(); continue; }
            Vec3 pos = arc.point;
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            if (arc.profile.animation() != MeleeArcProfile.Animation.NONE)
                pos = mob.getPosition(partial).add(0, mob.getBbHeight()*.5, 0).add(arc.direction.scale(sample.offsetMultiplier()));
            if (arc.profile.animation() == MeleeArcProfile.Animation.SLASH)
                pos = pos.add(arc.direction.z * sample.lateralOffset(), 0, -arc.direction.x * sample.lateralOffset());
            if (arc.copied != null) {
                var looseProfile=com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile.resolveForStack(arc.copied,true);
                if(looseProfile==null) { it.remove(); continue; }
                PoseStack ip=event.getPoseStack(); ip.pushPose(); try { ip.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z);
                ip.mulPose(Axis.YP.rotation((float)Math.atan2(arc.direction.x,arc.direction.z)+(arc.rotation+(float)sample.rotationDelta())*(float)Math.PI/180));
                com.juicyslew.moonstation14.ms14.loose_objects.client.WorldItemPresentationRenderer.render(arc.copied,looseProfile,ip,buffers,15728880,mc.level,0,(float)sample.alpha());
                } finally { ip.popPose(); } continue;
            }
            PoseStack pose = event.getPoseStack(); pose.pushPose(); try { pose.translate(pos.x-camera.x,pos.y-camera.y,pos.z-camera.z);
            pose.mulPose(Axis.YP.rotation((float)Math.atan2(arc.direction.x,arc.direction.z)+(arc.rotation+(float)sample.rotationDelta())*(float)Math.PI/180)); pose.mulPose(Axis.XP.rotationDegrees(90));
            float size = .5f;
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(arc.texture));
            var matrix = pose.last().pose(); int alpha = Math.round((float)(255*sample.alpha()));
            consumer.addVertex(matrix,-size,-size,0).setColor(255,255,255,alpha).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,-size,0).setColor(255,255,255,alpha).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,size,size,0).setColor(255,255,255,alpha).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            consumer.addVertex(matrix,-size,size,0).setColor(255,255,255,alpha).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(0,0,1);
            } finally { pose.popPose(); }
        }
        buffers.endBatch();
    }
    public static void clear() { ARCS.clear(); }
    public static void reconcileWorld(net.minecraft.client.multiplayer.ClientLevel level) {
        if (observedLevel != level) { ARCS.clear(); observedLevel = level; }
    }
    public static void clearPredicted() { ARCS.removeIf(a -> a.epoch > 0); }
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
