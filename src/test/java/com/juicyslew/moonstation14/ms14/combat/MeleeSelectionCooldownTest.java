package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSelectionCooldownTest {
    @Test void stagesDetachedCopyAndKeepsLaterCooldown() {
        var data = new MeleeWeaponData(Map.of("blunt", 1f), 1f, 1.5f, false, false, 1f, false);
        var profile = new MeleeWeaponProfile(data, Map.of(), Optional.empty());
        ItemStack source = new ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(ModDataComponents.MELEE_COOLDOWN.get(), new MeleeCooldownData(50, true));
        ItemStack staged = MeleeSelectionCooldown.stage(source, profile, 0);
        assertNotSame(source, staged);
        assertEquals(50, staged.get(ModDataComponents.MELEE_COOLDOWN.get()).nextAttackTick());
        assertEquals(50, source.get(ModDataComponents.MELEE_COOLDOWN.get()).nextAttackTick());
        assertTrue(staged.get(ModDataComponents.MELEE_COOLDOWN.get()).swingLeft());
    }

    @Test void falseAndMissingProfileDoNotStage() {
        var data = new MeleeWeaponData(Map.of("blunt", 1f), 1f, 1.5f, false, false, 1f, false,
                MeleeSoundData.DEFAULT, ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust"), 1f, 0f, .5f, false);
        ItemStack source = new ItemStack(net.minecraft.world.item.Items.STICK);
        assertSame(source, MeleeSelectionCooldown.stage(source, new MeleeWeaponProfile(data, Map.of(), Optional.empty()), 0));
        assertSame(source, MeleeSelectionCooldown.stage(source, null, 0));
    }
}
