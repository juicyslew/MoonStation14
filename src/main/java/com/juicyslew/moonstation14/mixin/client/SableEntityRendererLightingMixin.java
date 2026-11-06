package com.juicyslew.moonstation14.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.juicyslew.moonstation14.ms14.shuttle.sable.PackedLightAccountingScope;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EntityRenderer.class, priority = 500)
public abstract class SableEntityRendererLightingMixin {
    @WrapMethod(method = "getPackedLightCoords(Lnet/minecraft/world/entity/Entity;F)I")
    private int moonstation14$accountPlotLightOnce(Entity entity, float partialTick,
                                                   Operation<Integer> original) {
        PackedLightAccountingScope.begin(this);
        try {
            return original.call(entity, partialTick);
        } finally {
            PackedLightAccountingScope.end();
        }
    }
}
