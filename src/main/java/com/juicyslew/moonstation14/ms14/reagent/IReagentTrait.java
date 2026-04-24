package com.juicyslew.moonstation14.ms14.reagent;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

public interface IReagentTrait {
    // TODO: NOTE: This setup doesn't prevent me from calling item.toHandleSelf. So I have to always remember to use the right method for the right type of object. There is a way to make this explicit, but for now it seemed like interface overkill.

    float getCapacity();

    default ReagentHandle toHandle(ItemStack stack) {
        return new ReagentHandle(stack, this);
    }

    default ReagentHandle toHandleSelf() {
        return new ReagentHandle(this, this);
    }
}
