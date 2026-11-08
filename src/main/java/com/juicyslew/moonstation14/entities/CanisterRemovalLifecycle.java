package com.juicyslew.moonstation14.entities;

import net.minecraft.world.entity.Entity;

/** Removal hook implemented by gas canisters that need to flush state before removal. */
public interface CanisterRemovalLifecycle {
    /** Return false to defer removal until the exact slot payload can be safely released. */
    boolean beforeCanisterRemoval(Entity.RemovalReason reason);
}

