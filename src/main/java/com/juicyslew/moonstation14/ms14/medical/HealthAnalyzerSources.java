package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Bounded adapter for scanner roots actually owned by a controlled body. */
public final class HealthAnalyzerSources {
    public enum Kind { HELD, STORED }
    public record Source(LivingEntity root, Kind kind, ItemStack stack) {
        public Source { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    private HealthAnalyzerSources() { }

    public static Optional<Source> findInBody(LivingEntity body, UUID scannerId) {
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || scannerId == null) return Optional.empty();
        List<ItemStack> held = new ArrayList<>();
        for (String hand : hands.handIds()) hands.stackCopy(hand).ifPresent(held::add);
        for (ItemStack stack : held) if (matches(stack, scannerId)) return Optional.of(new Source(body, Kind.HELD, stack));
        for (ItemStack stack : held) {
            Optional<Source> child = stored(body, stack, scannerId);
            if (child.isPresent()) return child;
        }
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isPresent()) for (var slot : equipment.orElseThrow().slots()) if (slot.stack().isPresent()) {
            ItemStack host = slot.stack().orElseThrow();
            if (matches(host, scannerId)) return Optional.of(new Source(body, Kind.HELD, host));
            Optional<Source> child = stored(body, host, scannerId);
            if (child.isPresent()) return child;
        }
        return Optional.empty();
    }

    private static Optional<Source> stored(LivingEntity body, ItemStack host, UUID id) {
        return GridContents.read(host).stream().flatMap(grid -> grid.entries().stream())
                .map(GridContents.Entry::stack).filter(stack -> matches(stack, id))
                .findFirst().map(stack -> new Source(body, Kind.STORED, stack));
    }
    private static boolean matches(ItemStack stack, UUID id) {
        var data = stack.get(ModDataComponents.HEALTH_ANALYZER.get());
        return data != null && data.scannerId().equals(id);
    }
}
