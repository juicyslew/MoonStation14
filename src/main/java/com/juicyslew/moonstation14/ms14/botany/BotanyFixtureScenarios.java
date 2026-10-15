package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Explicit, bounded debug-only plant arrangements for the permission-gated fixture command. */
public final class BotanyFixtureScenarios {
    private BotanyFixtureScenarios() { }

    public static String arrange(ServerLevel level, BlockPos origin) {
        if (!ModSeedProfiles.serverReady() || !ModTrayProfiles.serverReady()) return "Scenario refused: resolved seed/tray profiles are not ready.";
        var tomato = ModSeedProfiles.serverLookup(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"));
        var wheat = ModSeedProfiles.serverLookup(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"));
        if (tomato == null || wheat == null) return "Scenario refused: resolved tomato/wheat profiles are unavailable.";
        HydroponicsTrayBlockEntity tomatoTray = tray(level, SmallBotanyFixtureLayout.normalTray(origin, false));
        HydroponicsTrayBlockEntity wheatTray = tray(level, SmallBotanyFixtureLayout.normalTray(origin, true));
        HydroponicsTrayBlockEntity weedsTray = tray(level, SmallBotanyFixtureLayout.dryTray(origin, false));
        HydroponicsTrayBlockEntity lowTray = tray(level, SmallBotanyFixtureLayout.dryTray(origin, true));
        if (tomatoTray == null || wheatTray == null || weedsTray == null || lowTray == null
                || tomatoTray.plantState() == null || wheatTray.plantState() == null
                || weedsTray.plantState() == null || lowTray.plantState() == null)
            return "Scenario refused: fixture trays are not initialized; initialize atmosphere, then wait for normal tray initialization and retry.";
        setReady(tomatoTray, ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), tomato, level.getGameTime());
        setReady(wheatTray, ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), wheat, level.getGameTime());
        PlantInstance weeds = weedsTray.plantState();
        weeds.soil(0, 0, 6, 0); weeds.touch(); weedsTray.commitMutation();
        var low = lowTray.plantState();
        low.plant(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), tomato, level.getGameTime());
        low.soil(15, 8, 0, 40);
        low.state(1, tomato.biology().endurance()/2, 0, 0, false, false);
        lowTray.commitMutation();
        return "Arranged labeled fixture scenarios: repeat-harvest tomato, wheat harvest, empty dry/infertile weedy tray, and planted low-resource/toxin tomato. Harvest plants were arranged by bounded real metabolism cycles at 293.15 K/100 kPa (debug setup, not elapsed natural growth); the untouched tray remains available for natural growth.";
    }

    private static HydroponicsTrayBlockEntity tray(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity tray && tray.isLoadedValid() ? tray : null;
    }
    private static void setReady(HydroponicsTrayBlockEntity tray, ResourceLocation id, SeedProfile seed, long now) {
        PlantInstance plant = tray.plantState();
        plant.plant(id, seed, now);
        plant.soil(100, 100, 0, 0);
        TrayProfile profile = tray.resolvedTrayProfile();
        var random = new FixtureRandom();
        for (int i=0; i<256 && !plant.ready() && !plant.dead(); i++)
            PlantMetabolismEvaluator.cycle(plant, profile, seed, now + (i+1L)*profile.cycleDelayTicks(), random,
                    Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f)));
        tray.commitMutation();
    }
    private static final class FixtureRandom implements RandomGenerator {
        @Override public long nextLong() { return 0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public int nextInt(int origin, int bound) { return origin; }
        @Override public float nextFloat() { return 0; }
    }
}
