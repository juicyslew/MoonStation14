package com.juicyslew.moonstation14.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;

/** Marker for SableRemotePlotProjectionPlugin's verified post-merge packet callback. */
@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class LooseCargoPacketFrameMixin { }
