package com.juicyslew.moonstation14.structs;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ConstructionResult {
    public Block resultBlock;
    public SoundEvent interactionSound;
    public int requiredStack;
    public Item extraDropItem;
    public int extraDropAmount;

    public ConstructionResult(Block resultBlock, SoundEvent interactionSound, int required_stack, Item extraDropItem, int extraDropAmount) {
        this.resultBlock = resultBlock;
        this.interactionSound = interactionSound;
        this.requiredStack = required_stack;
        this.extraDropItem = extraDropItem;
        this.extraDropAmount = extraDropAmount;
    }
}
