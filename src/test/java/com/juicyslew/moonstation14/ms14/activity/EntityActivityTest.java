package com.juicyslew.moonstation14.ms14.activity;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityActivityTest {
    @Test
    void metabolismCoversEveryResidueExactlyOnce() {
        int due = 0;
        for (int entityId = 0; entityId < EntityActivity.REAGENT_METABOLISM.tickInterval(); entityId++) {
            if (EntityActivity.REAGENT_METABOLISM.isDue(0L, entityId)) {
                due++;
            }
        }
        assertEquals(1, due);

        for (int residue = 0; residue < 20; residue++) {
            int matches = 0;
            for (int entityId = 0; entityId < 20; entityId++) {
                if (EntityActivity.REAGENT_METABOLISM.isDue(residue, entityId)) {
                    matches++;
                }
            }
            assertEquals(1, matches, "residue " + residue + " must have one due entity");
        }
    }

    @Test
    void dueSchedulingIsSafeForNegativeInputs() {
        assertTrue(EntityActivity.REAGENT_METABOLISM.isDue(-1L, -19));
        assertFalse(EntityActivity.REAGENT_METABOLISM.isDue(-1L, 0));
        assertTrue(EntityActivity.STATUS_EFFECT.isDue(-100L, 100));
    }

    @Test
    void nonLivingEntitiesCannotEnableActivity() {
        ItemEntity item = new ItemEntity(EntityType.ITEM, null);

        EntityActivitySystem.update(item, EntityActivity.STATUS_EFFECT, true);

        assertFalse(item.hasData(ModDataAttachments.ACTIVE_SYSTEMS.get()));
    }

    @Test
    void stomachBindingSchedulesOnlyNonemptyReagentMetabolism() {
        var binding = MS14Bridges.STOMACH.activityBinding().orElseThrow();

        assertEquals(EntityActivity.REAGENT_METABOLISM, binding.activity());
        assertFalse(binding.needsTicking().test(new ReagentAttachment()));
        var reagent = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
        assertTrue(binding.needsTicking().test(new ReagentAttachment(java.util.Map.of(reagent, 1f))));
    }
}
