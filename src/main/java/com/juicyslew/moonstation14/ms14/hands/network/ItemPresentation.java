package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** An appearance-only, defensively copied stack for read-only snapshots. */
public final class ItemPresentation {
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPresentation> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(ItemPresentation::new, ItemPresentation::stack);
    private final ItemStack value;

    private ItemPresentation(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(ModDataComponents.GRID_CONTENTS.get());
        copy.remove(ModDataComponents.POUCH_CONTENTS.get());
        copy.remove(DataComponents.CONTAINER);
        copy.remove(DataComponents.CONTAINER_LOOT);
        copy.remove(DataComponents.BUNDLE_CONTENTS);
        copy.remove(DataComponents.CHARGED_PROJECTILES);
        copy.remove(DataComponents.ENTITY_DATA);
        copy.remove(DataComponents.BLOCK_ENTITY_DATA);
        copy.remove(DataComponents.BUCKET_ENTITY_DATA);
        copy.remove(DataComponents.BEES);
        this.value = copy;
    }

    public static ItemPresentation fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Presentation requires a non-empty stack");
        return new ItemPresentation(stack);
    }

    public ItemStack stack() { return value.copy(); }

    @Override public boolean equals(Object other) {
        return other instanceof ItemPresentation presentation && ItemStack.matches(value, presentation.value);
    }
    @Override public int hashCode() { return 0; }
}
