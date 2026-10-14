package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.botany.PlantInstance;
import com.juicyslew.moonstation14.ms14.botany.SeedProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BotanyExaminePresentationTest {
    private static final ResourceLocation SEED = ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat");
    private static final Component NAME = Component.translatable("item.moonstation14.wheat_seeds");

    @Test void reportsEveryStatusAndUsesActualResolvedSeedItemName() {
        for (PlantInstance.Status status : PlantInstance.Status.values()) {
            var entries = BotanyExaminePresentation.format(status, NAME, seed(), 0, 0, 0, 0);
            String expected = "examine.moonstation14.botany.status." + status.name().toLowerCase();
            if (status == PlantInstance.Status.EMPTY) {
                assertEquals(expected, key(entries.getFirst().text()));
                assertEquals(2, entries.size());
                assertEquals("examine.moonstation14.botany.resourcesWithoutHealth", key(entries.get(1).text()));
            } else {
                assertEquals("examine.moonstation14.botany.plant", key(entries.getFirst().text()));
                var arguments = ((TranslatableContents) entries.getFirst().text().getContents()).getArgs();
                assertSame(NAME, arguments[0], "resolved packet item's display component is retained");
                assertEquals(expected, key(entries.get(1).text()));
            }
        }
    }

    @Test void emptyPlantReportsCurrentResourcesWithoutPlantIdentityOrHealthDetail() {
        var entries = BotanyExaminePresentation.format(PlantInstance.Status.EMPTY, NAME, seed(), 12, 34, 0, 7);
        assertEquals(2, entries.size());
        assertEquals("examine.moonstation14.botany.status.empty", key(entries.get(0).text()));
        assertEquals("examine.moonstation14.botany.resourcesWithoutHealth", key(entries.get(1).text()));
        assertArrayEquals(new Object[]{12, 34, 7}, ((TranslatableContents) entries.get(1).text().getContents()).getArgs());
    }

    @Test void emptyPlantPreservesActualZeroResources() {
        var entries = BotanyExaminePresentation.format(PlantInstance.Status.EMPTY, null, null, 0, 0, 0, 0);
        assertArrayEquals(new Object[]{0, 0, 0}, ((TranslatableContents) entries.get(1).text().getContents()).getArgs());
    }

    @Test void reportsLiveResourcesAndHealthAgainstResolvedEndurance() {
        var entries = BotanyExaminePresentation.format(PlantInstance.Status.GROWING, NAME, seed(), 12.4f, 67.6f, 43.2f, 2.7f);
        assertEquals("examine.moonstation14.botany.resourcesWithoutHealth", key(entries.get(2).text()));
        assertArrayEquals(new Object[]{12, 68, 3}, ((TranslatableContents) entries.get(2).text().getContents()).getArgs());
        assertEquals("examine.moonstation14.botany.health", key(entries.get(3).text()));
        assertArrayEquals(new Object[]{43, 80}, ((TranslatableContents) entries.get(3).text().getContents()).getArgs());
        assertEquals(0, entries.stream().filter(entry -> key(entry.text()).equals("examine.moonstation14.botany.resources")).count());
        assertEquals(1, entries.stream().filter(entry -> key(entry.text()).equals("examine.moonstation14.botany.health")).count());
    }

    @Test void zeroPlantHealthDoesNotAddPointlessHealthDetail() {
        var entries = BotanyExaminePresentation.format(PlantInstance.Status.GROWING, NAME, seed(), 12, 34, 0, 7);
        assertEquals(0, entries.stream().filter(entry -> key(entry.text()).equals("examine.moonstation14.botany.health")).count());
    }

    @Test void roundsResourcesAtHalfUsingNearestInteger() {
        var entries = BotanyExaminePresentation.format(PlantInstance.Status.GROWING, NAME, seed(), 12.5f, 67.5f, 43.5f, 2.5f);
        assertArrayEquals(new Object[]{13, 68, 3}, ((TranslatableContents) entries.get(2).text().getContents()).getArgs());
    }

    @Test void projectionDoesNotMutatePlantState() {
        PlantInstance plant = new PlantInstance(34, 56, 123);
        long revision = plant.revision(), cycle = plant.lastCycle(), update = plant.nextUpdate();
        var soil = Map.of("water", plant.water(), "nutrition", plant.nutrition(), "health", plant.health());
        BotanyExaminePresentation.format(plant.status(), null, null, plant.water(), plant.nutrition(), plant.health(), plant.weeds());
        assertEquals(revision, plant.revision());
        assertEquals(cycle, plant.lastCycle());
        assertEquals(update, plant.nextUpdate());
        assertEquals(soil.get("water"), plant.water());
        assertEquals(soil.get("nutrition"), plant.nutrition());
        assertNull(plant.seed(), "formatting an uninitialized plant does not materialize a seed");
    }

    private static String key(Component component) { return ((TranslatableContents) component.getContents()).getKey(); }
    private static SeedProfile seed() {
        return new SeedProfile(SEED, ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat_bushel"), "wheat",
                new SeedProfile.Biology(80, 1, 1, 293, 10, 81, 121, 5, 10, 4),
                new SeedProfile.Lifecycle(6, 100, 10, 5, 1, 0, SeedProfile.Harvest.REPEAT), Map.of());
    }
}
