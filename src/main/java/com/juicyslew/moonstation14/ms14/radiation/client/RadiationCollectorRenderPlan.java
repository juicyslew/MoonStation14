package com.juicyslew.moonstation14.ms14.radiation.client;

import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorMotion;
import net.minecraft.resources.ResourceLocation;

/** Two primary geometry draws (fixed shell, accepted panel), plus the small independent pressure material tile. */
public record RadiationCollectorRenderPlan(ResourceLocation shellModel, ResourceLocation panelModel,
                                           ResourceLocation pressureModel,
                                           float panelAngleDegrees, int pressureBand, boolean tankPresent) {
    private static final String MODEL_ROOT = "block/reactor_bindings/collector/";

    public static RadiationCollectorRenderPlan from(RadiationCollectorBlockEntity.CollectorVisualSnapshot view,
                                                      long now) {
        String shell = "shell_tank" + (view.tankPresent() ? 1 : 0) + "_pressure" + view.pressureBand();
        String panel = view.enabled() ? "panel_active" : "panel_inactive";
        String pressure = "pressure_overlay_p" + view.pressureBand();
        float angle = view.transitioning()
                ? RadiationCollectorMotion.angleDegrees(new RadiationCollectorMotion.State(true,
                    view.transitionTargetEnabled(), view.transitionStartedAt()), now)
                : view.enabled() ? 90 : 0;
        return new RadiationCollectorRenderPlan(location(shell), location(panel), location(pressure), angle,
                view.pressureBand(), view.tankPresent());
    }

    public int shellPanelDrawCount() { return 2; }

    private static ResourceLocation location(String name) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", MODEL_ROOT + name);
    }
}
