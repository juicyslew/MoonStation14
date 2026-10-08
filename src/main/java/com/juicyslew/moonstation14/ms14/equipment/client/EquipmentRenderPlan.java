package com.juicyslew.moonstation14.ms14.equipment.client;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.hands.network.BodyAppearanceSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Immutable visible render instructions shared by body render perspectives. */
public final class EquipmentRenderPlan {
    private EquipmentRenderPlan() { }

    public record Hand(String id, ItemStack stack) { }
    public enum Anchor { HEAD, FACE, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, BACK, WAIST, CHEST, HIP }
    public record Garment(String slot, ResourceLocation item, EquipmentItemData.AppearanceRegion region,
                          int color, List<Anchor> anchors, int layerOrder, java.util.Optional<EquipmentItemData.WornModel> wornModel) {
        public Garment { anchors = List.copyOf(anchors); }
        public Garment(String slot, ResourceLocation item, EquipmentItemData.AppearanceRegion region, int color, List<Anchor> anchors, int layerOrder) {
            this(slot, item, region, color, anchors, layerOrder, java.util.Optional.empty());
        }
    }
    public record Plan(List<Hand> hands, List<Garment> garments) {
        public Plan { hands = List.copyOf(hands); garments = orderedGarments(garments); }
    }

    public static Plan create(BodyAppearanceSnapshot snapshot, PrototypeCatalog<EquipmentItemData> catalog) {
        var hands = new ArrayList<Hand>();
        if (snapshot != null) for (var entry : snapshot.hands()) {
            if (entry.item() != null) hands.add(new Hand(entry.id(), entry.item().stack()));
        }
        var garments = new ArrayList<Garment>();
        if (snapshot != null) for (var entry : snapshot.equipment()) {
            if (entry.item() == null || entry.id().equals("pocket1") || entry.id().equals("pocket2")) continue;
            ItemStack stack = entry.item().stack();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            EquipmentItemData data = catalog.get(itemId);
            if (data != null) data.clothing().ifPresent(clothing -> {
                var anchors = anchors(entry.id(), clothing.region());
                if (!anchors.isEmpty()) garments.add(new Garment(entry.id(), itemId, clothing.region(),
                        clothing.color(), anchors, layerOrder(entry.id()), clothing.wornModel()));
            });
        }
        return new Plan(hands, garments);
    }

    public static int layerOrder(String slot) {
        return switch (slot) {
            case "jumpsuit" -> 10;
            case "outerClothing" -> 20;
            case "back" -> 25;
            case "belt" -> 30;
            case "neck" -> 40;
            case "id" -> 41;
            case "suitstorage" -> 42;
            case "gloves", "shoes" -> 15;
            case "head", "ears", "eyes", "mask" -> 60;
            default -> 70;
        };
    }

    static List<Garment> orderedGarments(List<Garment> garments) {
        return garments.stream().sorted(java.util.Comparator.comparingInt(Garment::layerOrder)
                .thenComparing(Garment::slot).thenComparing(garment -> garment.item().toString())).toList();
    }

    /** Ordered clothing that should be drawn over one selected articulated arm. */
    public static List<Garment> armGarments(Plan plan, boolean left) {
        Anchor arm = left ? Anchor.LEFT_ARM : Anchor.RIGHT_ARM;
        return plan.garments().stream().filter(garment -> garment.anchors().contains(arm)).toList();
    }

    public static List<Anchor> anchors(String slot, EquipmentItemData.AppearanceRegion region) {
        if (!regionFits(slot, region)) return List.of();
        return switch (slot) {
            case "head" -> List.of(Anchor.HEAD);
            case "eyes", "mask" -> List.of(Anchor.FACE);
            case "ears" -> List.of(Anchor.HEAD);
            case "neck" -> List.of(Anchor.CHEST);
            case "jumpsuit" -> List.of(Anchor.TORSO, Anchor.LEFT_ARM, Anchor.RIGHT_ARM, Anchor.LEFT_LEG, Anchor.RIGHT_LEG);
            case "outerClothing" -> List.of(Anchor.TORSO, Anchor.LEFT_ARM, Anchor.RIGHT_ARM);
            case "gloves" -> List.of(Anchor.LEFT_ARM, Anchor.RIGHT_ARM);
            case "shoes" -> List.of(Anchor.LEFT_LEG, Anchor.RIGHT_LEG);
            case "back" -> List.of(Anchor.BACK);
            case "belt" -> List.of(Anchor.WAIST);
            case "id" -> List.of(Anchor.CHEST);
            case "suitstorage" -> List.of(Anchor.HIP);
            default -> List.of();
        };
    }

    /** Supported resolved appearance-region contract shared by planning and cuboid rendering. */
    public static boolean regionFits(String slot, EquipmentItemData.AppearanceRegion region) {
        if (region == null) return false;
        return switch (slot) {
            case "head" -> region == EquipmentItemData.AppearanceRegion.HEAD;
            case "eyes" -> region == EquipmentItemData.AppearanceRegion.EYES;
            case "mask" -> region == EquipmentItemData.AppearanceRegion.FACE;
            case "ears" -> region == EquipmentItemData.AppearanceRegion.EARS;
            case "neck" -> region == EquipmentItemData.AppearanceRegion.NECK;
            case "gloves" -> region == EquipmentItemData.AppearanceRegion.HANDS;
            case "shoes" -> region == EquipmentItemData.AppearanceRegion.FEET;
            case "jumpsuit", "outerClothing", "back", "belt", "id", "suitstorage" -> region == EquipmentItemData.AppearanceRegion.TORSO;
            default -> false;
        };
    }
}
