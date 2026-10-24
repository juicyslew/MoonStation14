package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Detached display projection; carries no plant state and cannot initialize or mutate a tray. */
final class BotanyExaminePresentation {
    record Entry(Component text, int priority) { }
    private BotanyExaminePresentation() { }

    static Component plantViabilityNotice(boolean dead, SeedProfile seed) {
        return !dead && seed != null && !seed.lifecycle().viable()
                ? Component.translatable("examine.moonstation14.botany.plant.unviable") : null;
    }

    static Component plantLigneousNotice(boolean dead, SeedProfile seed) {
        return !dead && seed != null && seed.lifecycle().ligneous()
                ? Component.translatable("examine.moonstation14.botany.plant.ligneous") : null;
    }

    static Component plantConcern(PlantInstance plant, SeedProfile seed) {
        if (plant.dead() || seed == null) return null;
        if (seed.biology().endurance() > 0 && plant.health() <= seed.biology().endurance() / 2
                && plant.age() > seed.lifecycle().lifespan())
            return Component.translatable("examine.moonstation14.botany.plant.old");
        if (seed.biology().endurance() > 0 && plant.health() <= seed.biology().endurance() / 2)
            return Component.translatable("examine.moonstation14.botany.plant.unhealthy");
        return null;
    }

    static List<Entry> warnings(PlantInstance plant, SeedProfile seed, boolean drawWarnings,
                               java.util.Optional<PlantMetabolismEvaluator.Environment> environment,
                               PlantMetabolismEvaluator.GasExchangeReport gasReport) {
        if (plant == null || plant.seed() == null || plant.dead()) return List.of();
        ArrayList<Entry> entries = new ArrayList<>();
        if (drawWarnings && plant.soilToxins() > 40)
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.toxins"), 62));
        if (plant.pests() >= 5)
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.pests"), 61));
        if (plant.weeds() >= 5)
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.weeds"), 61));
        if (drawWarnings && seed != null && environment.isPresent()) {
            var exposure = PlantMetabolismEvaluator.improperEnvironment(seed, environment.get());
            if (exposure.temperature()) entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.temperature"), 60));
            if (exposure.pressure()) entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.pressure"), 59));
        }
        if (drawWarnings && environment.isPresent() && gasReport.attempted()
                && !gasReport.unmetRequiredGases().isEmpty())
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.warning.gas"), 58));
        return List.copyOf(entries);
    }

    static List<Entry> format(PlantInstance.Status status, Component seedName, SeedProfile seed,
                              float water, float nutrition, float health, float weeds) {
        ArrayList<Entry> entries = new ArrayList<>();
        if (status == PlantInstance.Status.EMPTY) {
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.status.empty"), 70));
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.resourcesWithoutHealth",
                    Math.round(water), Math.round(nutrition), Math.round(weeds)), 68));
            return List.copyOf(entries);
        }
        if (seedName != null) entries.add(new Entry(Component.translatable("examine.moonstation14.botany.plant", seedName), 70));
        entries.add(new Entry(Component.translatable("examine.moonstation14.botany.status." + status.name().toLowerCase()), 69));
        entries.add(new Entry(Component.translatable("examine.moonstation14.botany.resourcesWithoutHealth",
                Math.round(water), Math.round(nutrition), Math.round(weeds)), 68));
        if (health > 0 && seed != null && seed.biology().endurance() > 0)
            entries.add(new Entry(Component.translatable("examine.moonstation14.botany.health", Math.round(health), Math.round(seed.biology().endurance())), 67));
        return List.copyOf(entries);
    }
}
