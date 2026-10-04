package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Immutable admitted snapshot for the deliberately closed set of body construction tools. */
public record BodyUseItemContext(ServerPlayer actor, HandActorAuthority.Snapshot authority, LiveHands hands,
        String handId, String token, long revision, ItemStack stack, BlockPos pos, Direction face) {
    public BodyUseItemContext {
        stack = stack.copy();
        pos = pos.immutable();
    }
    @Override public ItemStack stack() { return stack.copy(); }
}
