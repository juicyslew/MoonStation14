package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.world.item.ItemStack;

/** Shared duration and admitted-hand snapshot rules for door pry interactions. */
final class DoorPrySupport {
    private DoorPrySupport() { }
    static int durationTicks(double baseSeconds,double targetModifier,double speedModifier) {
        if(!Double.isFinite(baseSeconds)||baseSeconds<=0||!Double.isFinite(targetModifier)||targetModifier<=0||!Double.isFinite(speedModifier)||speedModifier<=0)return 0;
        double ticks=baseSeconds*targetModifier/speedModifier*20.0;
        return !Double.isFinite(ticks)||ticks<=0?0:(int)Math.ceil(ticks);
    }
    static boolean stablePhase(AirlockState.DoorPhase phase) {
        return phase==AirlockState.DoorPhase.OPEN||phase==AirlockState.DoorPhase.CLOSED;
    }
    static boolean matchesEmptyHandSnapshot(LiveHands hands,String hand,String expectedToken,long expectedRevision) {
        return hands!=null&&hands.revision()==expectedRevision&&hand!=null&&hand.equals(hands.activeHand())
                &&BodyHandRequestService.blockUseHandStateMatches(expectedToken,hands.token(hand).isPresent(),hands.stackCopy(hand).isPresent(),hands.token(hand).orElse(null));
    }
    static boolean matchesHeldHandSnapshot(LiveHands hands,String hand,String token,ItemStack captured,long revision) {
        return hands!=null&&revision==hands.revision()&&hand!=null&&hand.equals(hands.activeHand())&&token!=null&&captured!=null
                &&hands.token(hand).filter(token::equals).isPresent()&&hands.stackCopy(hand).filter(stack->ItemStack.isSameItemSameComponents(stack,captured)).isPresent();
    }
}
