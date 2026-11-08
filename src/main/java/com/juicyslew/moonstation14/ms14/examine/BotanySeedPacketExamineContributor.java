package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.ms14.botany.GeneticCarriers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;

/** Examine information for an actual loose seed packet, never merely genetics-bearing items. */
final class BotanySeedPacketExamineContributor {
    private BotanySeedPacketExamineContributor() { }

    static void contribute(ExamineContext context, ExamineCollector collector) {
        if (!context.detailsAllowed() || !(context.targetEntity() instanceof ItemEntity item)) return;
        var packet = GeneticCarriers.resolveSeedPacket(item.getItem());
        if (packet == null) return;
        var life = packet.profile().lifecycle();
        collector.detailEntry(Component.translatable("examine.moonstation14.botany.seed_packet.yield", life.yield()), "botany-seed-packet", 70);
        collector.detailEntry(Component.translatable("examine.moonstation14.botany.seed_packet.potency", life.potency()), "botany-seed-packet", 69);
    }
}
