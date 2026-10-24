package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.NativeHeldItemTransfer;
import com.juicyslew.moonstation14.ms14.loose_objects.pulling.BodyPullService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.juicyslew.moonstation14.MoonStation14;

/** Authoritative domain effects for entry to a non-conscious medical state. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class MedicalIncapacitationEffects {
    private MedicalIncapacitationEffects() { }

    @SubscribeEvent
    public static void onStateChanged(MobStateChangedEvent event) {
        if (event.current() == MobStateComponent.State.ALIVE) return;
        var body = event.body();
        if (body.level().isClientSide()) return;
        BodyPullService.stopForIncapacitation(body);
        dropTypedHands(body);
        // Native hands are a separate owner from typed body hands; never project/copy them into LiveHands.
        NativeHeldItemTransfer.forceDrop(body);
    }

    /** Upstream DropHandItemsEvent's typed-hand equivalent, directed at the event receiver. */
    public static void dropTypedHands(net.minecraft.world.entity.LivingEntity body) {
        while (true) {
            LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (hands == null || !hands.compatible(body)) break;
            boolean dropped = false;
            for (String hand : hands.handIds()) {
                String token = hands.token(hand).orElse(null);
                if (token == null) continue;
                BodyHandItemTransfer.Result result = BodyHandItemTransfer.forceDropOwned(
                        body, hand, token, hands.revision());
                if (result == BodyHandItemTransfer.Result.SUCCESS) { dropped = true; break; }
            }
            if (!dropped) break;
        }
    }

    /** Full source-directed DropHandItemsEvent projection; native hands stay with their owner. */
    public static void dropSlipSourceHands(net.minecraft.world.entity.LivingEntity body) {
        dropTypedHands(body);
        NativeHeldItemTransfer.forceDrop(body);
    }
}
