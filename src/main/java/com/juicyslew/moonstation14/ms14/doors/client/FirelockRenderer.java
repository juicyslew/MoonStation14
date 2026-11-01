package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.doors.FirelockState;
import com.juicyslew.moonstation14.ms14.doors.FirelockController;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Authored R4 firelock rendering. Host bindings supply the authoritative snapshot and warning clocks. */
public final class FirelockRenderer {
    private static final String BASE = "block/firelock/standard/";
    private static final float MIN_X = -.25f / 16f, MAX_X = 16.25f / 16f;
    private FirelockRenderer() { }
    private static ModelResourceLocation model(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, BASE + path));
    }
    public static List<ModelResourceLocation> additionalModels() {
        ArrayList<ModelResourceLocation> result = new ArrayList<>();
        for (String group : List.of("closed", "midpoint", "open")) {
            result.add(model(group));
            for (int warning = 0; warning <= 3; warning++) for (int phase = 0; phase <= 1; phase++)
                result.add(model("states/" + group + "_w" + warning + "_f" + phase));
        }
        result.add(model("states/closed_unlatched_w3"));
        result.add(model("states/closed_both_p1_t0"));
        result.add(model("states/closed_both_p0_t1"));
        for (String component : List.of("upper_leaf", "lower_leaf", "indicator_dark", "indicator_pressure_lit",
                "indicator_temperature_lit", "indicator_both_lit", "indicator_pressure_phase0", "indicator_pressure_phase1",
                "indicator_temperature_phase0", "indicator_temperature_phase1", "indicator_both_p0_t0",
                "indicator_both_p0_t1", "indicator_both_p1_t0", "indicator_both_p1_t1"))
            result.add(model("components/" + component));
        return List.copyOf(result);
    }
    public static AABB renderBounds(net.minecraft.core.BlockPos pos) {
        return new AABB(pos.getX() - .25/16, pos.getY(), pos.getZ() - .25/16,
                pos.getX() + 1 + .25/16, pos.getY() + 2, pos.getZ() + 1 + .25/16);
    }
    /** Presentation-only policy for the host's ordinary airlock model layer. */
    public static boolean cullBaseAirlock(boolean latchedClosed, double q) {
        return latchedClosed && q == 0.0;
    }

    public static void renderPresentation(FirelockController.Presentation view, FirelockData data, double doubleTick,
                                          PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (view == null || data == null) return;
        FirelockFramePlanner.PresentationPlan plan=FirelockFramePlanner.plan(view,data,doubleTick);
        if(plan!=null)renderPlan(view.state(),plan,view.facing(),pose,buffers,light,overlay);
    }

    /** Draw API consumes controller-computed q (0 closed, 1 open); transitionAt is a deadline, never a start time. */
    public static void renderSnapshot(FirelockState state, FirelockData data, double q, boolean latchedClosed,
                                      long now, long pressureStartedAt,
                                      long temperatureStartedAt, Direction facing, PoseStack pose,
                                      MultiBufferSource buffers, int light, int overlay) {
        if (state == null || data == null || facing == null || !Double.isFinite(q)) return;
        q = Math.max(0, Math.min(1, q));
        var plan=FirelockFramePlanner.plan(state,q,latchedClosed,now,pressureStartedAt,temperatureStartedAt);
        renderPlan(state,plan,facing,pose,buffers,light,overlay);
    }
    private static void renderPlan(FirelockState state,FirelockFramePlanner.PresentationPlan plan,Direction facing,PoseStack pose,
                                   MultiBufferSource buffers,int light,int overlay){
        if(state==null||plan==null||facing==null)return;
        if(!plan.drawCover())return;
        FirelockFramePlanner.Travel travel=plan.travel();
        var manager = Minecraft.getInstance().getModelManager();
        pose.pushPose(); pose.translate(.5, 0, .5);
        // R4 export faces -Z: canonical local 180-degree conversion, then block facing.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot())); pose.translate(-.5, 0, -.5);
        AirlockBlockEntityRenderer.drawComponent(manager.getModel(model("components/lower_leaf")), pose, buffers,
                light, overlay, 0, travel.lowerY(), true, state.powered(), MIN_X, MAX_X, 0, 2, 0, 1);
        AirlockBlockEntityRenderer.drawComponent(manager.getModel(model("components/upper_leaf")), pose, buffers,
                light, overlay, 0, travel.upperY(), true, state.powered(), MIN_X, MAX_X, 0, 2, 0, 1);
        FirelockFramePlanner.Indicator selected = plan.indicator();
        String indicator = "indicator_" + selected.name().toLowerCase(java.util.Locale.ROOT);
        AirlockBlockEntityRenderer.drawComponent(manager.getModel(model("components/"+indicator)), pose, buffers,
                light, overlay, 0, travel.indicatorY(), true, FirelockFramePlanner.indicatorEmissive(state, plan), MIN_X, MAX_X, 0, 2, 0, 1);
        pose.popPose();
    }
}
