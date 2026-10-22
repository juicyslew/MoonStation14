package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcTaskPlannerTest {
    @Test
    void selectionHonorsNutritionPreconditionsAndFoodBranchPriority() {
        var policy = mousePolicy();
        var targets = List.of(
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 4, 1, false, true, true),
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.DRINK, 1, 2, false, true, true));

        assertEquals(NpcBehaviorComponent.Task.FOOD,
                NpcTaskPlanner.select(policy, 20, 10, 25, 15, targets).orElseThrow().task());
        assertEquals(NpcBehaviorComponent.Task.DRINK,
                NpcTaskPlanner.select(policy, 30, 10, 25, 15, targets).orElseThrow().task());
        assertEquals(NpcBehaviorComponent.Task.IDLE,
                NpcTaskPlanner.select(policy, 30, 20, 25, 15, targets).orElseThrow().task());
    }

    @Test
    void unsupportedAndInaccessibleTargetsAreRejected() {
        var policy = mousePolicy();
        var targets = List.of(new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 1, 1, false, true, false));
        assertEquals(NpcBehaviorComponent.Task.IDLE,
                NpcTaskPlanner.select(policy, 10, 100, 25, 15, targets).orElseThrow().task());
    }

    @Test
    void targetValueIsBinaryNearestValidWinsAndLivingMobIsNeverFood() {
        var policy = mousePolicy();
        var candidates = List.of(
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 2, 1, false, true, true),
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 3, 50, false, true, true),
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 0.1, 1, true, true, true),
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 1, 0, false, true, true),
                new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 10, 1, false, true, true));
        var selected = NpcTaskPlanner.select(policy, 10, 100, 15, 10, candidates).orElseThrow();
        assertEquals(2d, selected.target().distance());
    }

    @Test
    void foodAcquisitionRandomizesOnlyAClosestEligibleBoundedBand() {
        var near = new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 2, 1, false, true, true);
        var near2 = new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 3.5, 1, false, true, true);
        var far = new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 8, 1, false, true, true);
        var rejected = new NpcTaskPlanner.Candidate(NpcTaskPlanner.Need.FOOD, 2.1, 1, false, false, true);
        assertEquals(near, NpcTaskPlanner.selectNearbyFood(List.of(near, near2, far, rejected), 10, 0).orElseThrow());
        assertEquals(near2, NpcTaskPlanner.selectNearbyFood(List.of(near, near2, far, rejected), 10, .99).orElseThrow());
        assertEquals(far, NpcTaskPlanner.selectNearbyFood(List.of(far), 10, .5).orElseThrow());
    }

    @Test
    void omittedIdleTaskDoesNotInventAnIdleFallback() {
        var policy = new NpcBehaviorComponent(List.of(NpcBehaviorComponent.Task.FOOD), 10, 1, 7, 9, 40, 140);
        assertEquals(java.util.Optional.empty(), NpcTaskPlanner.select(policy, 30, 30, 15, 10, List.of()));
    }

    @Test
    void rejectedSourceCooldownsExpireWithoutSourceReappearingAndRemainBounded() {
        Map<UUID, Long> rejected = new HashMap<>();
        UUID first = null;
        for (int i = 0; i < 200; i++) {
            UUID source = UUID.randomUUID();
            if (i == 0) first = source;
            NpcBehaviorSystem.rejectSource(rejected, source, 100 + i);
        }
        assertEquals(128, rejected.size());
        assertTrue(rejected.containsValue(299L), "most recent failed source remains cooled down");

        NpcBehaviorSystem.expireRejectedSources(rejected, 400);
        assertTrue(rejected.isEmpty(), "removed sources do not need to reappear to release their UUID cache");
        NpcBehaviorSystem.rejectSource(rejected, first, 460);
        assertEquals(Map.of(first, 460L), rejected, "a formerly rejected source can be retried after its TTL");
    }

    @Test
    void autonomousMouseTasksUseResolvedSprintSpeedPerSs14SteeringContract() throws IOException {
        var movement = readMouseMovement();
        assertEquals(2.5d, movement.walkSpeed());
        assertEquals(5d, movement.sprintSpeed());
        assertEquals(5d, NpcBehaviorSystem.autonomousSpeedPerSecond(movement));
    }

    private static MovementSpeedModifierComponent readMouseMovement() throws IOException {
        String path = "data/moonstation14/moonstation14/character/mouse.json";
        try (var stream = NpcTaskPlannerTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing resource " + path);
            JsonObject mouse = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var component : mouse.getAsJsonArray("components")) {
                JsonObject json = component.getAsJsonObject();
                if (MovementSpeedModifierComponent.TYPE.equals(json.get("type").getAsString()))
                    return MovementSpeedModifierComponent.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            }
            throw new IOException("Missing movement component in " + path);
        }
    }

    private static NpcBehaviorComponent mousePolicy() {
        return new NpcBehaviorComponent(List.of(NpcBehaviorComponent.Task.FOOD,
                NpcBehaviorComponent.Task.DRINK, NpcBehaviorComponent.Task.IDLE), 10, 1, 7, 9, 40, 140);
    }
}
