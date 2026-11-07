package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Actual owned removable cell stack. Charge remains on the cell's BatteryState. */
public record GunPowerCellSlotState(Optional<ItemStack> cell) {
    public static final Codec<GunPowerCellSlotState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.optionalFieldOf("cell").forGetter(s -> s.cell)
    ).apply(i, GunPowerCellSlotState::new));
    public GunPowerCellSlotState {
        cell = cell == null ? Optional.empty() : cell.filter(s -> !s.isEmpty()).map(ItemStack::copy);
    }
    @Override public Optional<ItemStack> cell() { return cell.map(ItemStack::copy); }
}
