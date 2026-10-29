package com.juicyslew.moonstation14.ms14.botany.client;

import com.juicyslew.moonstation14.block.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Installs tray wrappers during the native model-bake epoch. */
public final class PlantTrayModelRegistration {
    private static final Logger LOGGER = Logger.getLogger(PlantTrayModelRegistration.class.getName());
    private PlantTrayModelRegistration() { }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        PlantModelManifest manifest = PlantModelManifest.load();
        List<ModelResourceLocation> trays = List.of(
                BlockModelShaper.stateToModelLocation(ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState()),
                BlockModelShaper.stateToModelLocation(ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState()));
        install(event.getModels(), manifest, trays);
    }

    /** Production helper shared by bake hook and focused tests. Mutates only the two supplied tray keys. */
    static void install(Map<ModelResourceLocation, BakedModel> bakedModels, PlantModelManifest manifest,
                        List<ModelResourceLocation> trayKeys) {
        Map<ModelResourceLocation, BakedModel> plants = new LinkedHashMap<>();
        manifest.plants().values().forEach(states -> states.values().forEach(id -> {
            BakedModel model = bakedModels.get(id);
            if (model != null) plants.put(id, model);
        }));
        PlantTrayBakedModel.NativePlantAssets assets = new PlantTrayBakedModel.NativePlantAssets(plants);
        for (ModelResourceLocation trayKey : trayKeys) {
            BakedModel original = bakedModels.get(trayKey);
            if (original == null) {
                LOGGER.warning("No baked tray model available for hydroponics model " + trayKey);
                continue;
            }
            bakedModels.put(trayKey, new PlantTrayBakedModel(original, manifest, assets));
        }
        for (var entry : manifest.plants().entrySet()) for (var state : entry.getValue().entrySet()) {
            if (!plants.containsKey(state.getValue())) LOGGER.warning("No native baked hydroponics plant model " + entry.getKey() + "/" + state.getKey());
        }
    }
}
