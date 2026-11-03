package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Derives clothing wear cues only from committed changes to genuinely worn slots. */
public final class EquipmentWearAudio {
    private EquipmentWearAudio() { }

    public record Cue(String sound, boolean equip) { }
    public record WearEntry(String slotId, String token, ResolvedWornEquipment.Entry gear) { }

    public static List<Cue> cues(List<WearEntry> before, List<WearEntry> after) {
        var old = wornBySlot(before);
        var next = wornBySlot(after);
        var result = new ArrayList<Cue>();
        for (var entry : old.entrySet()) {
            var replacement = next.get(entry.getKey());
            if (replacement == null || !replacement.token().equals(entry.getValue().token()))
                entry.getValue().gear().profile().clothing().flatMap(EquipmentItemData.Clothing::unequipSound)
                        .ifPresent(sound -> result.add(new Cue(sound, false)));
        }
        for (var entry : next.entrySet()) {
            var previous = old.get(entry.getKey());
            if (previous == null || !previous.token().equals(entry.getValue().token()))
                entry.getValue().gear().profile().clothing().flatMap(EquipmentItemData.Clothing::equipSound)
                        .ifPresent(sound -> result.add(new Cue(sound, true)));
        }
        return List.copyOf(result);
    }

    private static java.util.Map<String, WearEntry> wornBySlot(List<WearEntry> entries) {
        var result = new java.util.LinkedHashMap<String, WearEntry>();
        for (var entry : entries) {
            var clothing = entry.gear().profile().clothing().orElse(null);
            if (entry.token() != null && !entry.token().isBlank() && clothing != null
                    && entry.gear().slotFlags().stream().anyMatch(clothing.slotFlags()::contains))
                result.put(entry.slotId(), entry);
        }
        return result;
    }

    public static void afterCommitted(LivingEntity body, LiveHands before, LiveHands after) {
        if (body == null || before == null || after == null || !(body.level() instanceof ServerLevel level)) return;
        try {
            var template = com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveEquipmentTemplate(body).orElse(null);
            var oldSnapshot = LiveHands.equipmentSnapshot(body, before).orElse(null);
            var newSnapshot = LiveHands.equipmentSnapshot(body, after).orElse(null);
            if (template == null || oldSnapshot == null || newSnapshot == null) return;
            var catalog = ModEquipmentItems.catalog(level);
            for (var cue : cues(withTokens(oldSnapshot, ResolvedWornEquipment.entries(oldSnapshot, template, catalog)),
                    withTokens(newSnapshot, ResolvedWornEquipment.entries(newSnapshot, template, catalog)))) {
                var id = ResourceLocation.tryParse(cue.sound());
                if (id != null) com.juicyslew.moonstation14.ms14.audio.EntityAttachedSoundRouter.play(
                        level, body, id, SoundSource.PLAYERS, 1f, 1f);
            }
        } catch (RuntimeException ignored) {
            // Audio is an optional consequence of an already committed inventory mutation.
        }
    }

    private static List<WearEntry> withTokens(LiveHands.EquipmentSnapshot snapshot,
                                               List<ResolvedWornEquipment.Entry> entries) {
        var tokens = new java.util.HashMap<String, String>();
        snapshot.slots().forEach(slot -> slot.token().ifPresent(token -> tokens.put(slot.id(), token)));
        return entries.stream().map(entry -> new WearEntry(entry.slotId(), tokens.get(entry.slotId()), entry)).toList();
    }
}
