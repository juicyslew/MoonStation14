package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Camera-independent, bounded strike mesh geometry and submission. */
public final class MeleeStrikeTrailRenderer {
    public record RibbonLayer(List<Vec3> vertices, float alpha) { }
    public record EffectMesh(List<RibbonLayer> primary, List<RibbonLayer> secondary) { }
    public record RenderCounts(int primaryVertices, int guideVertices) { }
    private MeleeStrikeTrailRenderer() { }

    /** Builds a bold independent stroke plus a restrained, clipped reach guide. */
    public static EffectMesh mesh(Vec3 origin, MeleeArcClient.StrikeView strike) {
        if (origin == null || strike == null || strike.geometry() == null) return new EffectMesh(List.of(),List.of());
        var g = strike.geometry(); var s = strike.sample(); var p = strike.profile();
        Vec3 strikeDirection = NeutralMeleeStrikeEffectGeometry.capturedDirection(g, g.centerEndpoint());
        if (g.centerEndpoint().subtract(origin).dot(strikeDirection) < 0) return new EffectMesh(List.of(),List.of());
        double travel = NeutralMeleeStrikeEffectGeometry.travelFraction(p, s.progress());
        if (travel <= 0) return new EffectMesh(List.of(),List.of());
        Vec3 end = NeutralMeleeStrikeEffectGeometry.motionEndpoint(strike);
        float alpha = (float)Math.max(0, Math.min(1, s.trailAlpha()));
        if (!finite(origin) || !finite(end) || alpha <= 0) return new EffectMesh(List.of(),List.of());
        Vec3 selectedDirection = NeutralMeleeStrikeEffectGeometry.capturedDirection(g, end);
        if (end.subtract(origin).dot(selectedDirection) < 0) return new EffectMesh(List.of(),List.of());
        double visibleProgress = visibleProgress(strike);
        Vec3 tip = pointAt(origin, strike, visibleProgress);
        Vec3 clipped = end;
        double roll = Math.toRadians(strike.swingLeft() ? 35 : -35);
        List<RibbonLayer> ribbons = new ArrayList<>();
        double contactLength = clipped.subtract(origin).length();
        boolean shortContact = contactLength < p.trailWidth() * 6;
        double bandWidth = shortContact ? Math.max(p.trailWidth() * .18, p.trailWidth() * Math.min(.55, contactLength / (p.trailWidth() * 6))) : p.trailWidth();
        List<Vec3> main = MeleeStrikeRibbonGeometry.build(origin, tip, clipped, g.facingYaw(), roll, p.trailWidth(), false, bandWidth);
        if (!main.isEmpty()) ribbons.add(new RibbonLayer(main,alpha));
        List<Vec3> cross = shortContact ? List.of() : MeleeStrikeRibbonGeometry.build(origin, tip, clipped, g.facingYaw(), roll + Math.PI/2, p.trailWidth(), true);
        if (!cross.isEmpty()) ribbons.add(new RibbonLayer(cross,alpha*.6f));
        List<RibbonLayer> out = new ArrayList<>();
        for (double back : shortContact ? new double[0] : new double[]{.04,.08}) {
            double past = Math.max(0, visibleProgress - back);
            if (past >= visibleProgress) continue;
            Vec3 ghostTip = pointAt(origin, strike, past);
            List<Vec3> ghost = MeleeStrikeRibbonGeometry.build(origin, ghostTip, clipped, g.facingYaw(), roll,
                    p.trailWidth() * (back == .04 ? .5 : .35));
            if (!ghost.isEmpty()) out.add(new RibbonLayer(ghost, alpha * (back == .04 ? .2f : .1f)));
        }
        List<Vec3> guide = new ArrayList<>();
        if (g.mode() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE
                && !g.boundaryEndpoints().isEmpty()) {
            for (Vec3 boundary : g.boundaryEndpoints())
                guide.addAll(openGuide(origin, boundary, p.trailWidth() * .25));
        } else guide.addAll(openGuide(origin, g.centerEndpoint(), p.trailWidth() * .25));
        if (!guide.isEmpty()) out.add(new RibbonLayer(guide, .5f * alpha));
        return new EffectMesh(List.copyOf(ribbons),List.copyOf(out));
    }
    public static Vec3 tipPoint(Vec3 origin, MeleeArcClient.StrikeView strike) {
        if (strike == null) return origin;
        return pointAt(origin, strike, visibleProgress(strike));
    }
    private static double visibleProgress(MeleeArcClient.StrikeView strike) {
        double endpointCap = strike.geometry().mode() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE
                ? 1 : strike.profile().extensionEndFraction();
        return Math.min(strike.sample().progress(), endpointCap);
    }
    private static Vec3 pointAt(Vec3 origin, MeleeArcClient.StrikeView strike, double progress) {
        Vec3 endpoint = strike.geometry().mode() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE
                ? NeutralMeleeStrikeEffectGeometry.selectedEndpoint(strike)
                : strike.geometry().centerEndpoint();
        if (strike.geometry().mode() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE)
            return endpoint;
        return origin.lerp(endpoint, NeutralMeleeStrikeEffectGeometry.travelFraction(strike.profile(), progress));
    }
    public static int render(Vec3 origin, MeleeArcClient.StrikeView strike,
                             com.mojang.blaze3d.vertex.PoseStack pose,
                             net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 camera) {
        return renderCounts(origin,strike,pose,buffers,camera).primaryVertices();
    }
    public static RenderCounts renderCounts(Vec3 origin, MeleeArcClient.StrikeView strike,
                             com.mojang.blaze3d.vertex.PoseStack pose,
                             net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 camera) {
        var mesh=mesh(origin,strike); if(mesh.primary().isEmpty() && mesh.secondary().isEmpty()) return new RenderCounts(0,0);
        var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/white_pixel.png");
        var primaryConsumer=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture));
        int primary=0, guide=0;
        for(var layer:mesh.primary()) primary+=submitQuadVertices(layer.vertices(),layer.alpha(),primaryConsumer,pose,camera);
        for(var layer:mesh.secondary()) guide+=submitQuadVertices(layer.vertices(),layer.alpha(),primaryConsumer,pose,camera);
        return new RenderCounts(primary,guide);
    }
    private static int submitQuadVertices(List<Vec3> vertices,float opacity,
                              com.mojang.blaze3d.vertex.VertexConsumer consumer,
                              com.mojang.blaze3d.vertex.PoseStack pose, Vec3 camera) {
        if(vertices.size()<4) return 0;
        int submitted=0, alpha=Math.max(0,Math.min(255,Math.round(opacity*255))); var matrix=pose.last().pose();
        for(int i=0;i+3<vertices.size();i+=4) {
            Vec3 a=vertices.get(i),b=vertices.get(i+1),c=vertices.get(i+2),d=vertices.get(i+3);
            Vec3 normal=b.subtract(a).cross(c.subtract(a)); if(normal.lengthSqr()<1e-16) normal=c.subtract(a).cross(d.subtract(a));
            if(normal.lengthSqr()<1e-16) continue; normal=normal.normalize();
            for(int j=0;j<4;j++){ Vec3 v=vertices.get(i+j).subtract(camera);
                consumer.addVertex(matrix,(float)v.x,(float)v.y,(float)v.z).setColor(255,255,255,alpha)
                    .setUv(j==0||j==3?0:1,j<2?0:1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(15728880).setNormal((float)normal.x,(float)normal.y,(float)normal.z); submitted++;
            }
        }
        return submitted;
    }
    private static List<Vec3> openGuide(Vec3 start, Vec3 end, double width) {
        Vec3 axis=end.subtract(start);
        if (axis.lengthSqr()<1e-10 || !finite(start) || !finite(end) || !Double.isFinite(width) || width<=0) return List.of();
        axis=axis.normalize(); Vec3 ref=Math.abs(axis.y)<.92?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 u=axis.cross(ref).normalize().scale(width*.5), v=axis.cross(u).normalize().scale(width*.5);
        return List.of(start.add(u),start.subtract(u),end.subtract(u),end.add(u),
                start.add(v),start.subtract(v),end.subtract(v),end.add(v));
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
}
