package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

/** Integration hook for claim/protection mods before a body-held pipe placement is admitted. */
public final class BodyPipePlacementPermissionEvent extends Event implements ICancellableEvent {
    private final UUID controllerId;
    private final ServerPlayer controller;
    private final LivingEntity body;
    private final ItemStack bodyStack;
    private final BlockPos clicked;
    private final Direction face;
    private final BlockPos destination;

    public BodyPipePlacementPermissionEvent(ServerPlayer controller, LivingEntity body, ItemStack bodyStack,
                                            BlockPos clicked, Direction face, BlockPos destination) {
        this.controller = controller;
        this.controllerId = controller.getUUID();
        this.body = body;
        this.bodyStack = bodyStack.copy();
        this.clicked = clicked.immutable();
        this.face = face;
        this.destination = destination.immutable();
    }

    public UUID controllerId() { return controllerId; }
    public ServerPlayer controller() { return controller; }
    public LivingEntity body() { return body; }
    public ItemStack bodyStack() { return bodyStack.copy(); }
    public BlockPos clicked() { return clicked; }
    public Direction face() { return face; }
    public BlockPos destination() { return destination; }
}
