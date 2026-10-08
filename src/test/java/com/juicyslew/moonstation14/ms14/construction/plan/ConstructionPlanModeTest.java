package com.juicyslew.moonstation14.ms14.construction.plan;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionPlanModeTest {
    private ConstructionPlanRequest req(long seq, ConstructionPlanRequest.Action action, String recipe) {
        return new ConstructionPlanRequest(UUID.randomUUID(), 4, seq, action, recipe);
    }
    @Test void selectionReplacementRotationAndClearRetainMonotonicSequence() {
        var supported=(java.util.function.Predicate<String>) s -> s.equals("moonstation14:girder") || s.equals("moonstation14:steel_wall");
        var selected=ConstructionPlanMode.transition(null,req(1,ConstructionPlanRequest.Action.SELECT,"moonstation14:girder"),supported);
        assertEquals("moonstation14:girder",selected.recipe());
        var rotated=ConstructionPlanMode.transition(selected,req(2,ConstructionPlanRequest.Action.ROTATE,""),supported);
        assertEquals(1,rotated.rotation());
        var replaced=ConstructionPlanMode.transition(rotated,req(3,ConstructionPlanRequest.Action.SELECT,"moonstation14:steel_wall"),supported);
        assertEquals("moonstation14:steel_wall",replaced.recipe());assertEquals(0,replaced.rotation());
        var cleared=ConstructionPlanMode.transition(replaced,req(4,ConstructionPlanRequest.Action.CLEAR,""),supported);
        assertNull(cleared.recipe());assertEquals(4,cleared.sequence());
        assertSame(cleared,ConstructionPlanMode.transition(cleared,req(4,ConstructionPlanRequest.Action.SELECT,"moonstation14:girder"),supported));
        assertSame(cleared,ConstructionPlanMode.transition(cleared,req(5,ConstructionPlanRequest.Action.SELECT,"moonstation14:item"),supported));
    }
    @Test void clearAndItemCraftDoNotInventPlanTransitions() {
        var supported=(java.util.function.Predicate<String>) s -> s.startsWith("moonstation14:structure");
        var active=ConstructionPlanMode.transition(null,req(8,ConstructionPlanRequest.Action.SELECT,"moonstation14:structure_girder"),supported);
        // Item crafting has no plan action; state remains unchanged until an explicit intent arrives.
        assertEquals("moonstation14:structure_girder",active.recipe());
        assertSame(active,ConstructionPlanMode.transition(active,req(8,ConstructionPlanRequest.Action.SELECT,"moonstation14:structure_girder"),supported));
    }
    @Test void disposalBridgeProjectsResolvedGoalEntityNotRecipeName() {
        var graph=new com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph("Start",java.util.Map.of(
                "Start",new com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.Node(java.util.Optional.empty(),java.util.List.of()),
                "Goal",new com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.Node(java.util.Optional.of("moonstation14:disposal_bend"),java.util.List.of())));
        assertEquals("moonstation14:disposal_bend",ConstructionPlanMode.resolvedEntityPrototype(graph,"Goal"));
        assertNull(ConstructionPlanMode.resolvedEntityPrototype(graph,"Start"));
    }
    @Test void requestAuthorityRequiresExactBodyAndEpoch() {
        UUID body=UUID.randomUUID();
        assertTrue(ConstructionPlanMode.matchesAuthority(body,4,body,4));
        assertFalse(ConstructionPlanMode.matchesAuthority(body,4,body,3));
        assertFalse(ConstructionPlanMode.matchesAuthority(body,4,UUID.randomUUID(),4));
    }
}
