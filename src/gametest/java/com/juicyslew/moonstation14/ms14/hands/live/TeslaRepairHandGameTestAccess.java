package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Narrow GameTest bridge for package-private immutable LiveHands hand seeding. */
public final class TeslaRepairHandGameTestAccess {
    private TeslaRepairHandGameTestAccess() { }
    public static void hold(LivingEntity body, String hand, String token, ItemStack stack) {
        LiveHands current=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var existingToken=current.token(hand);
        if(existingToken.isPresent()) {
            ItemStack existing=current.stackCopy(hand).orElseThrow();
            current=current.replaceActiveUseStack(current.revision(),hand,existingToken.orElseThrow(),existing,ItemStack.EMPTY)
                    .orElseThrow(()->new IllegalStateException("could not remove prior fixture-owned hand item"));
        }
        var result=current.putWhole(current.revision(),hand,new ItemToken(token),stack);
        if(!result.succeeded()) throw new IllegalStateException("could not seed repair tool");
        body.setData(ModDataAttachments.LIVE_HANDS.get(),result.state());
    }
}
