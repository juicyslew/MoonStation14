package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Detached display projection; carries no plant state and cannot initialize or mutate a tray. */
final class BotanyExaminePresentation {
    record Entry(Component text, int priority) { }
    private BotanyExaminePresentation() { }

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
