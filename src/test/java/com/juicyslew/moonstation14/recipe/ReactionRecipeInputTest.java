package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactionRecipeInputTest {
    @Test
    void ownsAnImmutableCopyOfItsSourceMap() {
        ResourceKey<ReagentData> reagent = ModReagents.createKey("recipe-input-copy");
        Map<ResourceKey<ReagentData>, Float> source = new HashMap<>(Map.of(reagent, 1f));

        ReactionRecipeInput input = new ReactionRecipeInput(source);
        source.put(ModReagents.createKey("after-construction"), 2f);

        assertEquals(Map.of(reagent, 1f), input.container());
        assertFalse(input.container().containsKey(ModReagents.createKey("after-construction")));
        assertThrows(UnsupportedOperationException.class,
                () -> input.container().put(reagent, 2f));
    }
}
