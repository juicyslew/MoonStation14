package com.juicyslew.moonstation14.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimal access to the native EditBox's viewport and selection anchor for shared painting. */
@Mixin(EditBox.class)
public interface EditBoxDisplayAccessor {
    @Accessor("highlightPos") int ms14$highlightPos();
    @Accessor("displayPos") int ms14$displayPos();
}
