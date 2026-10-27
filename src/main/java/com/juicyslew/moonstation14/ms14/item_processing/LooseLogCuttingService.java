package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.tools.ToolQualities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Objects;

/** One-shot SS14 LogComponent behavior for an authenticated, nominated loose source. */
public final class LooseLogCuttingService {
    private LooseLogCuttingService() { }

    /** Null means this target has no resolved Log composition; non-null means the action was handled. */
    public static BodyHandActionResult.Reason use(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, LooseItemEntity target, ItemStack held) {
        if (actor == null || authority == null || hands == null || request == null || target == null || held == null)
            return BodyHandActionResult.Reason.DENIED;
        var body = authority.body();
        var level = body.level() instanceof ServerLevel server ? server : null;
        var id = BuiltInRegistries.ITEM.getKey(target.getItem().getItem());
        var profiles = ModItemProcessingProfiles.serverCatalog();
        var profile = id == null || profiles == null ? null : profiles.get(id);
        if (profile == null || profile.log() == null) return null;
        var proof = LooseItemProcessingProof.capture(actor, authority, hands, request, target, held,
                BodyHandActionRequest.Action.USE_TARGET, tool -> tool.sharp() != null);
        if (proof == null) return BodyHandActionResult.Reason.DENIED;
        var log = profile.log();
        var outputItem = BuiltInRegistries.ITEM.get(log.output());
        if (outputItem == null || outputItem == net.minecraft.world.item.Items.AIR
                || !log.output().equals(BuiltInRegistries.ITEM.getKey(outputItem)))
            return BodyHandActionResult.Reason.DENIED;
        var origin = proof.origin();
        var output = new ArrayList<ItemStack>(log.count());
        for (int i = 0; i < log.count(); i++) output.add(new ItemStack(outputItem));
        var result = FreshWorldOutputBatch.publishAtLooseSource(level, origin, output,
                proof::current,
                target::discard);
        return switch (result) {
            case SUCCESS -> BodyHandActionResult.Reason.OK;
            case DENIED -> BodyHandActionResult.Reason.DENIED;
            case RECOVERY_REQUIRED -> BodyHandActionResult.Reason.RECOVERY_REQUIRED;
        };
    }

}
