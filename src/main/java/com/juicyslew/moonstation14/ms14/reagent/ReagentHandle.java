package com.juicyslew.moonstation14.ms14.reagent;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

public record ReagentHandle(Object holder, IReagentTrait trait) {
}
