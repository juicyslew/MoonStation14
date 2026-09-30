package com.juicyslew.moonstation14.mixin;

import com.juicyslew.moonstation14.ms14.hands.quarantine.ParkedInventoryDirectPolicy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Direct Inventory APIs only. Public compartment lists and mutable stack references remain bypasses. */
@Mixin(Inventory.class)
public abstract class InventoryParkedDirectGuardMixin {
    private boolean moonstation14$parkedDirect() {
        return ParkedInventoryDirectPolicy.deny((Inventory) (Object) this);
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyAdd(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(false);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyAddAt(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(false);
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyRemove(int slot, int amount, CallbackInfoReturnable<ItemStack> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyRemoveNoUpdate(int slot, CallbackInfoReturnable<ItemStack> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyRemoveReference(ItemStack stack, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denySet(int slot, ItemStack stack, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = {"dropAll()V", "clearContent()V"}, at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyNoArg(CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyPlaceBack(ItemStack stack, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyPlaceBackWithSync(ItemStack stack, boolean sync, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "replaceWith(Lnet/minecraft/world/entity/player/Inventory;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyReplace(Inventory other, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "clearOrCountMatchingItems(Ljava/util/function/Predicate;ILnet/minecraft/world/Container;)I",
            at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyClearMatching(Predicate<ItemStack> predicate, int limit, Container container,
                                                 CallbackInfoReturnable<Integer> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(0);
    }

    @Inject(method = "removeFromSelected(Z)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denySelectedRemoval(boolean wholeStack, CallbackInfoReturnable<ItemStack> callback) {
        if (moonstation14$parkedDirect()) callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "setPickedItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyPickedItem(ItemStack stack, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "pickSlot(I)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyPickSlot(int slot, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "swapPaint(D)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyHotbarScroll(double direction, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/nbt/ListTag;)V", at = @At("HEAD"), cancellable = true)
    private void moonstation14$denyLoad(ListTag list, CallbackInfo callback) {
        if (moonstation14$parkedDirect()) callback.cancel();
    }
}
