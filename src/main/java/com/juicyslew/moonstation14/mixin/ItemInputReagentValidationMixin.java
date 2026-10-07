package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

/** Validates reagent prototypes on command-created stacks, before commands can grant them. */
@Mixin(ItemInput.class)
public abstract class ItemInputReagentValidationMixin {
    private static final DynamicCommandExceptionType UNKNOWN_REAGENT = new DynamicCommandExceptionType(
            id -> Component.literal("Unknown reagent prototype " + id));
    private static final SimpleCommandExceptionType CATALOG_NOT_READY = new SimpleCommandExceptionType(
            Component.literal("Cannot validate reagent prototypes: server catalog is not ready"));

    @Inject(method = "createItemStack(IZ)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), require = 1)
    private void moonstation14$validateReagents(int count, boolean allowOversizedStacks,
                                                 CallbackInfoReturnable<ItemStack> callback)
            throws CommandSyntaxException {
        ItemStack stack = callback.getReturnValue();
        ReagentComponent contents = stack.get(ModDataComponents.REAGENT);
        if (contents == null) return;
        PrototypeCatalog<ReagentData> catalog = PrototypeRuntime.serverReagents();
        if (catalog.size() == 0 && contents.centContents().values().stream().anyMatch(amount -> amount > 0))
            throw CATALOG_NOT_READY.create();
        for (var entry : contents.centContents().entrySet()) {
            if (entry.getValue() <= 0) continue;
            ResourceLocation id = entry.getKey().location();
            if (catalog.get(id) == null) throw UNKNOWN_REAGENT.create(id);
        }
    }
}
