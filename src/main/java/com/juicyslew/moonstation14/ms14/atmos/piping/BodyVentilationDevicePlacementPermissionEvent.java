package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Independent claim/protection hook for placement of a body-held ventilation device. */
public final class BodyVentilationDevicePlacementPermissionEvent extends Event implements ICancellableEvent {
    private final ServerPlayer controller;
    private final LivingEntity body;
    private final ItemStack heldItem;
    private final BlockPos clicked;
    private final Direction face;
    private final BlockPos destination;
    private final VentilationDeviceConfig.Kind kind;

    public BodyVentilationDevicePlacementPermissionEvent(ServerPlayer controller, LivingEntity body,
            ItemStack heldItem, BlockPos clicked, Direction face, BlockPos destination,
            VentilationDeviceConfig.Kind kind) {
        this.controller = controller;
        this.body = body;
        this.heldItem = heldItem.copy();
        this.clicked = clicked.immutable();
        this.face = face;
        this.destination = destination.immutable();
        this.kind = kind;
    }
    public ServerPlayer controller() { return controller; }
    public LivingEntity body() { return body; }
    public ItemStack heldItem() { return heldItem.copy(); }
    public BlockPos clicked() { return clicked; }
    public Direction face() { return face; }
    public BlockPos destination() { return destination; }
    public VentilationDeviceConfig.Kind kind() { return kind; }
}

