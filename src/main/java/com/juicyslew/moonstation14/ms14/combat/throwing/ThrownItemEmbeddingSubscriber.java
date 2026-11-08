package com.juicyslew.moonstation14.ms14.combat.throwing;

import com.juicyslew.moonstation14.entities.ItemAttachment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Converts only resolved embed-on-throw contacts to item-owned attachments. */
@EventBusSubscriber
public final class ThrownItemEmbeddingSubscriber {
    private ThrownItemEmbeddingSubscriber() {}
    @SubscribeEvent public static void onHit(ThrownItemHitEvent event) {
        var embedding=event.profile().embedding().orElse(null);
        if(embedding==null || !embedding.embedOnThrow() || !event.item().activeThrownCombat()) return;
        if(event.item().attachTo(event.target(),event.contactPoint(),event.incomingVelocity(), ItemAttachment.AttachmentKind.EMBEDDED))
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new ThrownItemEmbeddedEvent(event.item(),event.target(),event.profile(),event.throwerUUID()));
    }
}
