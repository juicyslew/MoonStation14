package com.juicyslew.moonstation14.ms14.explosion.grenade.server;

import com.juicyslew.moonstation14.ms14.explosion.core.BlastPlanner;
import com.juicyslew.moonstation14.ms14.explosion.prototype.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrenadeDetonationServiceTest {
    @Test
    void bundledSourceHasMeasuredOpenSpaceFootprintAgainstReference() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE);
        JsonObject json;
        try (var stream = getClass().getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/explosive_source/ex_grenade.json")) {
            assertNotNull(stream);
            json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        manager.reload(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE, Map.of(ModExplosiveSources.EX_GRENADE_ID, json));
        ExplosiveSourceData data = GrenadeDetonationService.source(manager.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE));
        assertNotNull(data);
        BlastPlanner.Proposal proposal = data.proposal();
        BlastPlanner.Plan live = BlastPlanner.plan(new BlastPlanner.Pos(0, 0, 0), proposal, p -> BlastPlanner.Cell.OPEN);
        BlastPlanner.Plan reference = BlastPlanner.plan(new BlastPlanner.Pos(0, 0, 0),
                new BlastPlanner.Proposal(BlastPlanner.REFERENCE_BUDGET, BlastPlanner.REFERENCE_SLOPE,
                        BlastPlanner.REFERENCE_CAP), p -> BlastPlanner.Cell.OPEN);
        assertTrue(live.successful());
        assertEquals(129, live.visited());
        assertEquals(129, live.intensities().size());
        assertEquals(380, live.intensityUsed());
        assertEquals(4, live.intensities().keySet().stream()
                .mapToInt(p -> Math.abs(p.x()) + Math.abs(p.y()) + Math.abs(p.z())).max().orElseThrow());
        assertEquals(2.0 / 33.0, live.intensities().get(new BlastPlanner.Pos(4, 0, 0)), 1e-12);
        assertTrue(reference.successful());
        assertEquals(25, reference.visited());
        assertEquals(25, reference.intensities().size());
        assertEquals(2, reference.intensities().keySet().stream()
                .mapToInt(p -> Math.abs(p.x()) + Math.abs(p.y()) + Math.abs(p.z())).max().orElseThrow());
        assertEquals(BlastPlanner.REFERENCE_BUDGET, reference.intensityUsed());
    }

    @Test
    void missingSourceOrBehaviorFailsClosed() {
        PrototypeManager sources = new PrototypeManager();
        sources.register(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE);
        assertEquals(null, GrenadeDetonationService.source(sources.snapshot(ModExplosiveSources.EXPLOSIVE_SOURCE_TYPE)));
        PrototypeManager behaviors = new PrototypeManager();
        behaviors.register(ModExplosions.EXPLOSION_TYPE);
        ExplosiveSourceData source = new ExplosiveSourceData(380, 3, 10, ModExplosions.DEFAULT_ID);
        assertEquals(false, GrenadeDetonationService.isResolved(source, behaviors.snapshot(ModExplosions.EXPLOSION_TYPE)));
    }

    @Test
    void admissionOutcomesDoNotClaimWorldEffectsApplied() {
        assertEquals(2, GrenadeDetonationService.Outcome.values().length);
        assertEquals(GrenadeDetonationService.Outcome.QUEUED, GrenadeDetonationService.Outcome.values()[0]);
    }

    @Test
    void failedCosmeticEmissionDoesNotPreventLaterEmission() {
        boolean[] emitted = {false};
        GrenadeDetonationService.sendBestEffort(() -> { throw new IllegalStateException("packet failed"); });
        GrenadeDetonationService.sendBestEffort(() -> emitted[0] = true);
        assertTrue(emitted[0]);
    }
}
