package com.juicyslew.moonstation14.ms14.atmos.tank.client;

import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable component selection for rendering an authoritative canister appearance. */
public record GasCanisterRenderPlan(List<Component> components) {
    public record Component(ResourceLocation model, boolean fullbright) {
        public Component { Objects.requireNonNull(model); }
    }

    public GasCanisterRenderPlan { components = List.copyOf(components); }

    public static GasCanisterRenderPlan from(GasCanisterEntity.CanisterAppearance appearance) {
        Objects.requireNonNull(appearance);
        String skin = appearance.profile().modelSkinPrefix();
        ArrayList<Component> parts = new ArrayList<>();
        add(parts, skin, "shell", false);
        add(parts, skin, "head", false);
        add(parts, skin, "pressure_" + appearance.pressureBand(), true);
        add(parts, skin, appearance.profile().defaultLockedVisual() ? "lock_locked" : "lock_unlocked", false);
        if (appearance.tankInserted()) add(parts, skin, "tank_inserted", false);
        return new GasCanisterRenderPlan(parts);
    }

    private static void add(List<Component> parts, String skin, String name, boolean fullbright) {
        parts.add(new Component(ResourceLocation.fromNamespaceAndPath("moonstation14",
                "block/canisters/" + skin + "/components/" + name), fullbright));
    }
}
