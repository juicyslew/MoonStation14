package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import net.minecraft.world.level.Level;

/** Detached, side-effect-free held-container transfer staging shared by item and BE recipients. */
public record HeldLiquidTransferPlan(ReagentAttachment sourceAfter,
                                     ReagentAttachment recipientAfter,
                                     long admittedCents) {
    public HeldLiquidTransferPlan {
        if (admittedCents < 0) throw new IllegalArgumentException("invalid admitted amount");
    }

    /** Plans the existing proportional mixture transfer, then reacts only the detached recipient. */
    public static HeldLiquidTransferPlan plan(ReagentAttachment source, ReagentAttachment recipient,
                                               long capacityCents, float reactionCapacity, long requestedCents,
                                               Level reactionContext) {
        var transfer = LiquidInteractionCapability.plan(source.snapshotUnits(), recipient.snapshotUnits(),
                capacityCents, requestedCents);
        ReagentAttachment stagedSource = new ReagentAttachment(ReagentComponent.fromCents(transfer.sourceAfter()));
        ReagentAttachment stagedRecipient = new ReagentAttachment(ReagentComponent.fromCents(transfer.destinationAfter()));
        if (transfer.transferredCents() > 0)
            stagedRecipient.recursiveReaction(reactionContext, reactionCapacity);
        return new HeldLiquidTransferPlan(stagedSource, stagedRecipient, transfer.transferredCents());
    }
}
