package com.juicyslew.moonstation14.ms14.botany.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Immutable server-authored grinder view. Item stacks are defensively copied at both boundaries. */
public record GrinderSnapshot(UUID sessionId, UUID placement, UUID body, long bodyEpoch,
        ResourceLocation dimension, BlockPos position, long stateRevision, long beakerRevision,
        List<Slot> chamber, boolean powered, boolean active, long startTick, long endTick,
        boolean canGrind, boolean canJuice, com.juicyslew.moonstation14.ms14.botany.processing.GrinderProgram.Operation activeOperation,
        boolean open, String rejection) {
    public GrinderSnapshot {
        position = position.immutable();
        chamber = List.copyOf(chamber);
    }
    public record Slot(int index, long revision, UUID proof, ItemStack item) {
        public Slot { item = item.copy(); }
        @Override public ItemStack item() { return item.copy(); }
    }
}
