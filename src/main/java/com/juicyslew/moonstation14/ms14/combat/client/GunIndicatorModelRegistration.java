package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/** Installs the additive effect on charge variants as well as dispatch roots, preserving native override selection. */
public final class GunIndicatorModelRegistration {
    private GunIndicatorModelRegistration() { }
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) { GunIndicatorRegions.load(net.minecraft.client.Minecraft.getInstance().getResourceManager()); GunPresentationBindings.load(); install(event.getModels()); }
    static void install(Map<ModelResourceLocation, BakedModel> models) {
        Map<BakedModel, GunIndicatorBakedModel> wrappers = new IdentityHashMap<>();
        List<GunIndicatorRegions.Region> laser = List.of(GunIndicatorRegions.named("charge_bar"), GunIndicatorRegions.named("rear_gold_line"), GunIndicatorRegions.named("ready_lamp"));
        List<GunIndicatorRegions.Region> shotgun = List.of(GunIndicatorRegions.named("shotgun_status"));
        List<GunIndicatorRegions.Region> mosin = List.of(GunIndicatorRegions.named("mosin_solution_present"));
        for (String root : List.of("weapon_laser_gun", "weapon_energy_shotgun")) {
            List<GunIndicatorRegions.Region> regions = root.equals("weapon_laser_gun") ? laser : shotgun;
            wrap(models, "moonstation14", root, regions, wrappers);
            wrap(models, "moonstation14", "item/" + root, regions, wrappers);
            for (int charge = 0; charge <= 4; charge++) wrap(models, "moonstation14", "item/guns/" + root + "_charge" + charge, regions, wrappers);
        }
        wrap(models, "moonstation14", "weapon_sniper_mosin", mosin, wrappers);
        wrap(models, "moonstation14", "item/weapon_sniper_mosin", mosin, wrappers);
        wrap(models, "moonstation14", "item/weapon_sniper_mosin_solution_present", mosin, wrappers);
    }
    private static void wrap(Map<ModelResourceLocation, BakedModel> models, String namespace, String path, List<GunIndicatorRegions.Region> regions, Map<BakedModel, GunIndicatorBakedModel> wrappers) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        for (var entry : models.entrySet()) {
            ModelResourceLocation key = entry.getKey();
            if (key.id().equals(id) && (key.getVariant().equals("inventory") || path.startsWith("item/")) && !(entry.getValue() instanceof GunIndicatorBakedModel)) entry.setValue(GunIndicatorBakedModel.wrap(entry.getValue(), regions, wrappers));
        }
    }
}
