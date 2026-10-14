package com.juicyslew.moonstation14.ms14.chemistry;

import net.minecraft.world.level.Level;

/** Local equivalent of the upstream FitsInDispenser item policy. */
public interface DispenserCompatibleItem {
    boolean fitsInDispenser(Level level);
}

