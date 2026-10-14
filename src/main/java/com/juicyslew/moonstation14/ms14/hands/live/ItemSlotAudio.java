package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Authoritative post-commit audio for item-slot ownership transfers. */
public final class ItemSlotAudio {
    public enum Transfer { INSERT, EJECT, SWAP }
    private ItemSlotAudio() { }
    public static ResourceLocation defaultInsert() { return com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.insert(); }
    public static ResourceLocation defaultEject() { return com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.eject(); }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    public static void emit(ServerLevel level, ResourceLocation event, double x, double y, double z, float volume) {
        ControlledSoundDispatch.emit(level, event, x, y, z, volume);
    }
    public static float decibels(float db) { return (float) Math.pow(10.0, db / 20.0); }
    /** Sound plan for a receipt-producing transfer. Rejected/rolled-back transfers have no cues. */
    public static java.util.List<ResourceLocation> committedCues(Transfer transfer, BodyMachineItemSlotTransfer.Result receipt,
            ResourceLocation insert, ResourceLocation eject) {
        if (receipt != BodyMachineItemSlotTransfer.Result.SUCCESS || transfer == null) return java.util.List.of();
        return switch (transfer) {
            case INSERT -> java.util.List.of(java.util.Objects.requireNonNull(insert));
            case EJECT -> java.util.List.of(java.util.Objects.requireNonNull(eject));
            case SWAP -> java.util.List.of(java.util.Objects.requireNonNull(eject), java.util.Objects.requireNonNull(insert));
        };
    }
}
