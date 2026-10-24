package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only projection of resolved revolver composition and its per-stack chamber state. */
public final class RevolverChamberPresentation {
    private RevolverChamberPresentation() { }

    public enum Status { LOADED, SPENT, MISSING }
    public enum Size { NORMAL, SMALL, TINY }
    public record ChamberIcon(int index, Status status, boolean current, int tint) { }
    public record Projection(Size size, int currentIndex, List<ChamberIcon> chambers) {
        public Projection { chambers = List.copyOf(chambers); }
    }
    public record Draw(String texture, int x, int y, int width, int height, int sourceWidth, int sourceHeight, int tint) { }

    private static final int GREEN = 0xff32cd32;
    private static final int YELLOW_A = 0xffd7df60, YELLOW_B = 0xffb68f0e;
    private static final int RED_A = 0xffd3745f, RED_B = 0xffb50e25;
    private static final int DARK_A = 0xff222222, DARK_B = 0xff000000;

    /** Null state means preview only; a present empty state remains empty and is never refilled. */
    public static Optional<Projection> project(GunProfile resolvedProfile, ItemStack defensiveStack,
                                                RevolverState state) {
        if (resolvedProfile == null || defensiveStack == null || defensiveStack.isEmpty()
                || resolvedProfile.revolverAmmoProvider().isEmpty()) return Optional.empty();
        int capacity = resolvedProfile.revolverAmmoProvider().orElseThrow().capacity();
        if (capacity < 1 || capacity > RevolverState.MAX_CAPACITY) return Optional.empty();
        RevolverState effective = state == null ? RevolverProviderKernel.initialize(resolvedProfile) : state;
        if (effective.chambers().size() != capacity || effective.index() < 0 || effective.index() >= capacity)
            return Optional.empty();
        var icons = new ArrayList<ChamberIcon>(capacity);
        for (int i = 0; i < capacity; i++) {
            var chamber = effective.chambers().get(i);
            Status status;
            if (chamber.physical().isPresent()) {
                ItemStack cartridge = chamber.physical().orElseThrow();
                status = Boolean.TRUE.equals(cartridge.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))
                        ? Status.SPENT : Status.LOADED;
            } else if (chamber.virtualLive()) status = Status.LOADED;
            else status = Status.MISSING;
            int tint = switch (status) {
                case LOADED -> i % 2 == 0 ? YELLOW_A : YELLOW_B;
                case SPENT -> i % 2 == 0 ? RED_A : RED_B;
                case MISSING -> i % 2 == 0 ? DARK_A : DARK_B;
            };
            icons.add(new ChamberIcon(i, status, i == effective.index(), tint));
        }
        return Optional.of(new Projection(capacity <= 20 ? Size.NORMAL : capacity <= 30 ? Size.SMALL : Size.TINY,
                effective.index(), icons));
    }

    /** Production painter plan using the native Minecraft texture resource path and source counter geometry. */
    public static List<Draw> drawPlan(Projection projection, int x, int y) {
        if (projection == null) return List.of();
        int sourceWidth = switch (projection.size()) { case NORMAL -> 5; case SMALL -> 4; case TINY -> 2; };
        int boxWidth = (int) Math.ceil(sourceWidth * 1.3f), boxHeight = (int) Math.ceil(12 * 1.3f);
        var draws = new ArrayList<Draw>(projection.chambers().size() * 2);
        for (int i = 0; i < projection.chambers().size(); i++) {
            var chamber = projection.chambers().get(i);
            int cellX = x + i * (boxWidth + 1);
            String icon = projection.size().name().toLowerCase(java.util.Locale.ROOT);
            String normal = texture(icon), spent = texture("empty");
            if (chamber.current()) draws.add(new Draw(normal, cellX, y, boxWidth, boxHeight,
                    sourceWidth, 12, GREEN));
            String statusTexture = chamber.status() == Status.SPENT ? spent : normal;
            int statusWidth = chamber.status() == Status.SPENT ? 5 : sourceWidth;
            draws.add(new Draw(statusTexture, cellX + (boxWidth - statusWidth) / 2,
                    y + (boxHeight - 12) / 2, statusWidth, 12, statusWidth, 12, chamber.tint()));
        }
        return List.copyOf(draws);
    }

    /** Full native texture resource location used directly by GuiGraphics.blit. */
    public static ResourceLocation textureId(String texture) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/" + texture + ".png");
    }

    private static String texture(String name) { return "ss14/interface/itemstatus/bullets/" + name; }
}
