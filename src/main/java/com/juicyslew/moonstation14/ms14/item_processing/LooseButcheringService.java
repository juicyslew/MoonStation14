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
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Authenticated timed KNIFE Butcherable consumer for loose item sources. */
public final class LooseButcheringService {
    private static final String KIND = "loose_butchering";
    private LooseButcheringService() { }

    /** Shared source-composition admission used by both the menu query and timed action. */
    public static ItemProcessingProfile.Butcherable butcherable(ItemStack source) {
        if (source == null || source.isEmpty() || source.getCount() != 1) return null;
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        var catalog = ModItemProcessingProfiles.serverCatalog();
        var profile = id == null || catalog == null ? null : catalog.get(id);
        var result = profile == null ? null : profile.butcherable();
        return supportsType(result) ? result : null;
    }

    /** Shared supported-type rule, deliberately narrower than sharp-tool admission. */
    public static boolean supportsType(ItemProcessingProfile.Butcherable butcherable) {
        return butcherable != null && butcherable.type() == ItemProcessingProfile.Butcherable.Type.KNIFE;
    }

    public static BodyHandActionResult.Reason use(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, LooseItemEntity target, ItemStack held) {
        if (actor == null || authority == null || hands == null || request == null || target == null || held == null)
            return BodyHandActionResult.Reason.DENIED;
        var body = authority.body();
        var level = body.level() instanceof ServerLevel server ? server : null;
        var butcherable = butcherable(target.getItem());
        if (butcherable == null) return BodyHandActionResult.Reason.DENIED;
        var itemId = BuiltInRegistries.ITEM.getKey(target.getItem().getItem());
        var catalog = ModItemProcessingProfiles.serverCatalog();
        var profile = itemId == null || catalog == null ? null : catalog.get(itemId);
        var targetId = target.getUUID();
        if (TimedInteractionSystem.cancelDuplicate(body, KIND, targetId.toString())) return BodyHandActionResult.Reason.OK;
        if (!butcherable.type().equals(ItemProcessingProfile.Butcherable.Type.KNIFE)) return BodyHandActionResult.Reason.DENIED;
        var proof = LooseItemProcessingProof.capture(actor, authority, hands, request, target, held,
                BodyHandActionRequest.Action.BUTCHER, tool -> tool.sharp() != null);
        if (proof == null) return BodyHandActionResult.Reason.DENIED;
        var tool = proof.toolProfile();
        var outputItems = new ArrayList<net.minecraft.world.item.Item>(butcherable.outputs().stream().mapToInt(ItemProcessingProfile.ItemOutput::count).sum());
        for (var output : butcherable.outputs()) {
            var item = BuiltInRegistries.ITEM.get(output.output());
            if (item == null || item == net.minecraft.world.item.Items.AIR) return BodyHandActionResult.Reason.DENIED;
            for (int i = 0; i < output.count(); i++) outputItems.add(item);
        }
        var origin = proof.origin();
        var source = proof.sourceStack();
        var heldStack = proof.heldStack();
        var ticks = Math.max(1, (int) Math.ceil(butcherable.delaySeconds() * tool.sharp().butcherDelayModifier() * 20));
        var result = TimedInteractionSystem.start(body, actor, request.sequence(), KIND, targetId.toString(), ticks,
                true, true, () -> proof.current(), () -> complete(proof, profile, tool, outputItems),
                Optional.of(target.blockPosition()), () -> { });
        return result == TimedInteractionEngine.StartResult.STARTED || result == TimedInteractionEngine.StartResult.DUPLICATE_CANCELLED
                ? BodyHandActionResult.Reason.OK : BodyHandActionResult.Reason.DENIED;
    }

    private static TimedInteractionSystem.Completion complete(LooseItemProcessingProof proof,
            ItemProcessingProfile profile, com.juicyslew.moonstation14.ms14.tools.ToolProfile tool,
            List<net.minecraft.world.item.Item> items) {
        var target = proof.source();
        var origin = proof.origin();
        if (!proof.current())
            return TimedInteractionSystem.Completion.DENIED;
        var outputs = items.stream().map(ItemStack::new).toList();
        var result = FreshWorldOutputBatch.publishAtSourcePosition((ServerLevel) proof.authority().body().level(), origin,
                outputs, proof::current,
                () -> { target.discard(); return com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS; },
                (entity, stack) -> com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS);
        return switch (result) {
            case SUCCESS -> TimedInteractionSystem.Completion.STOP;
            case DENIED -> TimedInteractionSystem.Completion.DENIED;
            case RECOVERY_REQUIRED -> TimedInteractionSystem.Completion.RECOVERY_REQUIRED;
        };
    }

}
