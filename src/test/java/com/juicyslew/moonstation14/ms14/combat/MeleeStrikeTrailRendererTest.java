package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikeTrailRenderer;
import com.juicyslew.moonstation14.ms14.combat.client.MeleeStrikeRibbonGeometry;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeStrikeTrailRendererTest {
    @Test void primaryStrokeTravelsOutwardAlongCapturedReachAndFadesWithoutReturning() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0,0,1), 0, 2,
                MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0,0,2), java.util.List.of());
        var profile = new MeleeStrikeProfile(1, .5, .8, .1, 1);
        var start = strike(capture, profile, 0);
        var moving = strike(capture, profile, .4);
        var reach = strike(capture, profile, .55);
        var fading = strike(capture, profile, .9);
        assertTrue(MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, start).primary().isEmpty());
        var stroke = MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, moving).primary().get(0);
        assertTrue(stroke.vertices().size()>4);
        assertTrue(stroke.vertices().stream().mapToDouble(v->v.z).max().orElse(0) > 0);
        var fadingMesh = MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, fading);
        var fadingRibbon=fadingMesh.primary().get(0);
        assertTrue(fadingRibbon.alpha() < stroke.alpha());
        assertTrue(fadingRibbon.vertices().stream().mapToDouble(v->v.z).min().orElse(0) >= stroke.vertices().stream().mapToDouble(v->v.z).min().orElse(0)-1e-6,
                "the effect fades at reach instead of returning to its source");
        assertEquals(MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, reach).primary().get(0).vertices(), fadingRibbon.vertices());
        assertTrue(fadingMesh.secondary().size()>=1);
        assertFalse(fadingMesh.primary().isEmpty(), "curved primary ribbon remains the main strike stroke");
        assertTrue(fadingMesh.secondary().stream().allMatch(layer -> layer.alpha() < fadingRibbon.alpha()
                && layer.vertices().size() % 4 == 0), "secondary geometry stays thin/faint open or ribbon quads");
        assertTrue(fadingMesh.secondary().stream().allMatch(layer -> layer.vertices().stream().allMatch(v -> Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z))));
    }
    private static com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView strike(
            MeleeArcGeometry.StrikeCapture capture, MeleeStrikeProfile profile, double progress) {
        return new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(java.util.UUID.randomUUID(),1,false,
                capture, profile, MeleeStrikeTimeline.sample(profile, 1, progress * profile.durationSeconds()), false);
    }
    private static double area(java.util.List<Vec3> vertices) {
        double result = 0;
        for (int i = 0; i + 3 < vertices.size(); i += 4) {
            Vec3 a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2), d = vertices.get(i + 3);
            result += b.subtract(a).cross(c.subtract(a)).length() * .5;
            result += c.subtract(a).cross(d.subtract(a)).length() * .5;
        }
        return result;
    }
    @Test void shortContactKeepsFullOuterCrescentButNarrowsFilledBandAndUsesSourceGuideOnly() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0,0,1), 0, 2,
                MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0,0,.2), java.util.List.of());
        var profile = new MeleeStrikeProfile(1, .5, .8, .1, 1);
        var view = strike(capture, profile, .4);
        var mesh = MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, view);
        assertEquals(1, mesh.primary().size(), "short contact has the crescent, not a cross stroke");
        assertEquals(1, mesh.secondary().size(), "short contact has only its source guide");
        var crescent = mesh.primary().get(0).vertices();
        var endpoint = MeleeStrikeTrailRenderer.tipPoint(Vec3.ZERO, view);
        var baseline = MeleeStrikeRibbonGeometry.build(Vec3.ZERO, endpoint, capture.centerEndpoint(), 0,
                Math.toRadians(-35), profile.trailWidth());
        assertEquals(baseline.size(), crescent.size());
        for (int i = 0; i < crescent.size(); i += 4) {
            assertEquals(baseline.get(i), crescent.get(i), "outer edge remains full-width");
            assertEquals(baseline.get(i + 3), crescent.get(i + 3), "outer edge remains full-width");
        }
        assertTrue(area(crescent) > 0 && area(crescent) < area(baseline),
                "actual filled band shrinks without shrinking outer style geometry");
        Vec3 axis = new Vec3(0,0,1);
        assertTrue(crescent.stream().allMatch(v -> v.z >= -1e-9 && v.z <= endpoint.z + 1e-9), "crescent stays between source and tip");
        var guide = mesh.secondary().get(0).vertices();
        assertEquals(Vec3.ZERO, guide.get(0).add(guide.get(1)).scale(.5), "guide cross-section starts at source");
    }
    @Test void longOpenStrokeKeepsCrossGhostsAndSourceToEndpointGuide() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0,0,1), 0, 2,
                MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0,0,2), java.util.List.of());
        var view = strike(capture, new MeleeStrikeProfile(1, .5, .8, .1, 1), .4);
        var mesh = MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, view);
        assertEquals(2, mesh.primary().size(), "long stroke retains main and cross ribbons");
        assertTrue(mesh.secondary().size() >= 3, "long stroke retains two ghosts and source guide");
        Vec3 endpoint = MeleeStrikeTrailRenderer.tipPoint(Vec3.ZERO, view);
        var guide = mesh.secondary().get(mesh.secondary().size() - 1).vertices();
        assertEquals(Vec3.ZERO, guide.get(0).add(guide.get(1)).scale(.5), "guide starts at source");
        assertEquals(capture.centerEndpoint(), guide.get(2).add(guide.get(3)).scale(.5), "guide reaches captured endpoint");
    }
    @Test void capturedCenterBehindMovedOriginSuppressesBackwardTrail() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0, 0, 1), 0, 2,
                MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0, 0, 2), java.util.List.of());
        var strike = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(
                java.util.UUID.randomUUID(), 1, false, capture,
                new MeleeStrikeProfile(1, .5, .8, .1, 1),
                new MeleeStrikeTimeline.Sample(1, .5, 1, false), false);
        assertTrue(MeleeStrikeTrailRenderer.mesh(new Vec3(0, 0, 3), strike).primary().isEmpty());
    }
    @Test void wideStrikeTipUsesSelectedBoundaryAndGuideCoversEveryFanBoundary() {
        var boundaries = java.util.List.of(new Vec3(-1, 0, 2), new Vec3(0, 0, 2), new Vec3(1, 0, 2));
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO,
                new Vec3(0, 0, 1), 0, 2, MeleeAttackRequest.Mode.WIDE, 0,
                new Vec3(0, 0, 2), boundaries);
        var profile = new MeleeStrikeProfile(1, .5, .8, .1, 1);
        for (boolean left : new boolean[]{false, true}) {
            for (double progress : new double[]{.25, .5, .8, .9}) {
                var view = strike(capture, profile, progress);
                view = new com.juicyslew.moonstation14.ms14.combat.client.MeleeArcClient.StrikeView(
                        view.body(), view.requestSequence(), view.predicted(), capture, profile, view.sample(), left);
                double fraction = MeleeStrikeTimeline.wideSweepFraction(profile, progress);
                var endpoint = boundaries.get(0).lerp(boundaries.get(2), left ? 1 - fraction : fraction);
                assertEquals(endpoint, MeleeStrikeTrailRenderer.tipPoint(Vec3.ZERO, view));
                var mesh = MeleeStrikeTrailRenderer.mesh(Vec3.ZERO, view);
                assertFalse(mesh.primary().isEmpty());
                assertTrue(mesh.primary().get(0).vertices().stream().allMatch(v -> v.distanceTo(endpoint) < 3));
                for (Vec3 boundary : boundaries)
                    assertTrue(mesh.secondary().stream().flatMap(layer -> layer.vertices().stream())
                            .mapToDouble(v -> v.distanceTo(boundary)).min().orElse(Double.POSITIVE_INFINITY) <= .126,
                            "guide omits fan endpoint " + boundary);
            }
        }
    }
    @Test void submitsPrimaryBeforeAcquiringSecondaryBuffer() {
        var capture = new MeleeArcGeometry.StrikeCapture(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, new Vec3(0,0,1), 0, 2,
                MeleeAttackRequest.Mode.LIGHT, 0, new Vec3(0,0,2), java.util.List.of());
        var strike = strike(capture, new MeleeStrikeProfile(1, .5, .8, .1, 1), .4);
        var source = mock(net.minecraft.client.renderer.MultiBufferSource.class);
        var generation = new int[]{0};
        var requests = new java.util.ArrayList<net.minecraft.client.renderer.RenderType>();
        when(source.getBuffer(any())).thenAnswer(invocation -> {
            int active = ++generation[0];
            requests.add(invocation.getArgument(0));
            return mock(com.mojang.blaze3d.vertex.VertexConsumer.class, invocation2 -> {
                if (invocation2.getMethod().getName().equals("addVertex") && active != generation[0])
                    throw new IllegalStateException("Not building!");
                return org.mockito.Answers.RETURNS_SELF.answer(invocation2);
            });
        });
        var counts = MeleeStrikeTrailRenderer.renderCounts(Vec3.ZERO, strike, new com.mojang.blaze3d.vertex.PoseStack(), source, Vec3.ZERO);
        assertTrue(counts.primaryVertices() > 0);
        assertTrue(counts.guideVertices() > 0);
        assertEquals(1, requests.size());
        var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","textures/gui/white_pixel.png");
        assertEquals(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture), requests.get(0));
    }
}
