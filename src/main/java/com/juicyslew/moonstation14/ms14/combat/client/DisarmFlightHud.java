package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Brief, screen-space indication of the initial direction of a participant's disarm flight. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class DisarmFlightHud {
    private DisarmFlightHud() { }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { DisarmFlightVisuals.clear(); }

    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) DisarmFlightVisuals.reconcileWorld(null);
        else DisarmFlightVisuals.reconcileWorld(mc.level.dimension().location());
    }

    static boolean admitted(boolean hasWorld, boolean participant, boolean hideGui, boolean ownedBody) {
        return hasWorld && participant && !hideGui && ownedBody;
    }

    static double bearing(net.minecraft.world.phys.Vec3 velocity, org.joml.Quaternionf cameraRotation) {
        var right = new org.joml.Vector3f(1, 0, 0).rotate(cameraRotation);
        var up = new org.joml.Vector3f(0, 1, 0).rotate(cameraRotation);
        var forward = new org.joml.Vector3f(0, 0, -1).rotate(cameraRotation);
        double rightDot = velocity.x * right.x + velocity.y * right.y + velocity.z * right.z;
        double upDot = velocity.x * up.x + velocity.y * up.y + velocity.z * up.z;
        double projected = rightDot * rightDot + upDot * upDot;
        if (projected < 1.0e-8) {
            double depth = velocity.x * forward.x + velocity.y * forward.y + velocity.z * forward.z;
            return depth >= 0 ? 0 : Math.PI;
        }
        return Math.atan2(rightDot, upDot);
    }

    static float[][] arrowArms(float angle, float radius) {
        float x = (float)Math.sin(angle), y = (float)-Math.cos(angle);
        float tipX = x * radius, tipY = y * radius;
        float backX = (float)Math.sin(angle + Math.PI), backY = (float)-Math.cos(angle + Math.PI);
        float tailX = tipX + backX * 5, tailY = tipY + backY * 5;
        float sideX = (float)Math.sin(angle + Math.PI / 2), sideY = (float)-Math.cos(angle + Math.PI / 2);
        return new float[][]{{tipX, tipY, tailX + sideX * 4, tailY + sideY * 4},
                {tipX, tipY, tailX - sideX * 4, tailY - sideY * 4}};
    }

    static float cueScale(DisarmFlightVisuals.Flight flight) { return flight != null && flight.actor() ? .68f : 1f; }
    static double bearing(DisarmFlightVisuals.Flight flight, org.joml.Quaternionf cameraRotation) {
        return bearing(flight.velocity(), cameraRotation);
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event == null || mc.level == null || mc.player == null) {
            if (mc.level == null) DisarmFlightVisuals.reconcileWorld(null);
            return;
        }
        DisarmFlightVisuals.reconcileWorld(mc.level.dimension().location());
        var cue = DisarmFlightVisuals.sample(System.nanoTime()).stream()
                .filter(DisarmFlightVisuals.Flight::participant).reduce((a, b) -> b).orElse(null);
        if (!admitted(mc.level != null, cue != null, mc.options.hideGui,
                GhostControlClient.ownedCharacterForPresentation() != null)) return;
        double angle = bearing(cue, mc.gameRenderer.getMainCamera().rotation());
        long age = System.nanoTime() - cue.started();
        float alpha = age <= DisarmFlightVisuals.FULL_VISIBILITY_NANOS ? 1f : (float)Math.max(0, Math.min(1, (DisarmFlightVisuals.CUE_LIFETIME_NANOS - age) / (double)(DisarmFlightVisuals.CUE_LIFETIME_NANOS - DisarmFlightVisuals.FULL_VISIBILITY_NANOS)));
        if (alpha <= 0) return;
        GuiGraphics g = event.getGuiGraphics();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int color = ((int)(255 * alpha) << 24) | 0xFFBE23;
        int outline = ((int)(255 * alpha) << 24) | 0x202020;
        float scale=cueScale(cue);
        for (float[] arm : arrowArms((float) angle, 40*scale)) {
            stroke(g, cx + arm[0], cy + arm[1], cx + arm[2], cy + arm[3], outline, Math.max(4,Math.round(5*scale)));
            stroke(g, cx + arm[0], cy + arm[1], cx + arm[2], cy + arm[3], color, Math.max(2,Math.round(3*scale)));
        }
        if(cue.actor()) {
            float[][] arrow=arrowArms((float)angle,22);
            for(float[] arm:arrow){stroke(g,cx+arm[0],cy+arm[1],cx+arm[2],cy+arm[3],outline,5);stroke(g,cx+arm[0],cy+arm[1],cx+arm[2],cy+arm[3],color,3);}
        }
        g.drawString(mc.font, net.minecraft.network.chat.Component.translatable("moonstation14.disarm.flight_direction"), cx + 47, cy - 4, color & 0xFFFFFF, true);
    }

    private static void stroke(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, int thickness) {
        double dx = x2 - x1, dy = y2 - y1;
        graphics.pose().pushPose();
        graphics.pose().translate(x1, y1, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)Math.atan2(dy, dx)));
        graphics.fill(0, -thickness / 2, (int)Math.ceil(Math.hypot(dx, dy)), thickness - thickness / 2, color);
        graphics.pose().popPose();
    }
}
