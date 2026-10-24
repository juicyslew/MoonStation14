package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;

/** Read-only tray status. Never asks the tray to initialize or tick its plant. */
public final class BotanyExamineContributor {
    private BotanyExamineContributor() { }

    public static void contribute(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed() || !(context.blockEntity() instanceof HydroponicsTrayBlockEntity tray)
                || !tray.isLoadedValid() || tray.resolvedTrayProfile() == null) return;

        PlantInstance plant = tray.plantState();
        if (plant == null) {
            collector.detailEntry(Component.translatable("examine.moonstation14.botany.status.empty"), "botany", 70);
            return;
        }
        if (plant.status() == PlantInstance.Status.EMPTY) {
            for (var entry : BotanyExaminePresentation.format(plant.status(), null, null,
                    plant.water(), plant.nutrition(), plant.health(), plant.weeds()))
                collector.detailEntry(entry.text(), "botany", entry.priority());
            return;
        }
        SeedProfile seed = plant.seed() == null ? null : HydroponicsTrayBlockEntity.resolvedPlantProfile(plant);
        Component viabilityNotice = BotanyExaminePresentation.plantViabilityNotice(plant.dead(), seed);
        if (viabilityNotice != null) collector.detailEntry(viabilityNotice, "botany", 66);
        Component ligneousNotice = BotanyExaminePresentation.plantLigneousNotice(plant.dead(), seed);
        if (ligneousNotice != null) collector.detailEntry(ligneousNotice, "botany", 65);
        Component concern = BotanyExaminePresentation.plantConcern(plant, seed);
        if (concern != null) collector.detailEntry(concern, "botany", 64);
        for (var warning : BotanyExaminePresentation.warnings(plant, seed, tray.resolvedTrayProfile().drawWarnings(),
                tray.cachedAppearanceEnvironment(), tray.gasExchangeReport()))
            collector.detailEntry(warning.text(), "botany", warning.priority());
        if (!plant.dead() && seed != null && seed.scream().enabled())
            collector.detailEntry(Component.translatable("examine.moonstation14.botany.plant.screams"), "botany", 63);
        Component plantName = seed == null ? Component.literal(plant.seed() == null ? "" : plant.seed().toString())
                : BuiltInRegistries.ITEM.getOptional(seed.packet()).map(item -> item.getDescription())
                    .orElseGet(() -> Component.literal(seed.packet().toString()));
        for (var entry : BotanyExaminePresentation.format(plant.status(), plantName, seed,
                plant.water(), plant.nutrition(), plant.health(), plant.weeds()))
            collector.detailEntry(entry.text(), "botany", entry.priority());
    }
}
