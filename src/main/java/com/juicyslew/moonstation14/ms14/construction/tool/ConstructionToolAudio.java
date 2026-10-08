package com.juicyslew.moonstation14.ms14.construction.tool;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative audio for a committed construction-tool operation. */
public final class ConstructionToolAudio {
    private ConstructionToolAudio() { }

    /** Call exactly once after timed work has successfully committed; canceled/denied work must not call this. */
    public static void playSuccessful(ServerLevel level, LivingEntity user, ItemStack held) {
        if (level == null || user == null || held == null || held.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getResourceKey(held.getItem())
                .map(key -> key.location()).orElse(null);
        if (itemId == null || !"moonstation14".equals(itemId.getNamespace())) return;
        ToolPrototypeData data = PrototypeRuntime.serverTools().get(ModTools.id(itemId.getPath()));
        if (data == null || data.useSound().isEmpty()) return;
        SoundEvent sound = switch (data.useSound().get()) {
            case Wrench -> ModSounds.TOOL_WRENCH.get();
            case Screwdriver -> ModSounds.TOOL_SCREWDRIVER.get();
            case Crowbar -> ModSounds.TOOL_CROWBAR.get();
            case Welder -> ModSounds.TOOL_WELDER.get();
        };
        level.playSound(null, user.getX(), user.getY(), user.getZ(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
