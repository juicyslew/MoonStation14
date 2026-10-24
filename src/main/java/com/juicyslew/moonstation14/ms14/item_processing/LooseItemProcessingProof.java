package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.tools.ModToolProfiles;
import com.juicyslew.moonstation14.ms14.tools.ToolProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.function.Predicate;

/** Captured common authority proof for a nominated loose item processing operation. */
public record LooseItemProcessingProof(ServerPlayer actor, HandActorAuthority.Snapshot authority,
        LiveHands hands, BodyHandActionRequest request, LooseItemEntity source, java.util.UUID sourceId,
        Vec3 origin, ItemStack sourceStack, ItemStack heldStack, ItemProcessingProfile profile,
        ToolProfile toolProfile, Predicate<ToolProfile> toolAdmission) {

    public static LooseItemProcessingProof capture(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LiveHands hands, BodyHandActionRequest request, LooseItemEntity source, ItemStack held,
            BodyHandActionRequest.Action action, Predicate<ToolProfile> toolAdmission) {
        if (actor == null || authority == null || hands == null || request == null || source == null || held == null
                || action == null || toolAdmission == null) return null;
        var body = authority.body();
        var level = body.level() instanceof ServerLevel server ? server : null;
        var id = BuiltInRegistries.ITEM.getKey(source.getItem().getItem());
        var processing = ModItemProcessingProfiles.serverCatalog();
        var profile = id == null || processing == null ? null : processing.get(id);
        var toolId = BuiltInRegistries.ITEM.getKey(held.getItem());
        var tool = toolId == null ? null : PrototypeRuntime.serverManager().snapshot(ModToolProfiles.TYPE).get(toolId);
        if (level == null || !level.getServer().isSameThread() || request.action() != action
                || request.expectedRevision() != hands.revision() || !request.handId().equals(hands.activeHand())
                || !Objects.equals(request.expectedToken(), hands.token(request.handId()).orElse(null))
                || !hands.compatible(body) || !CharacterControlSystem.canAct(body)
                || !HandActorAuthority.revalidate(actor, authority) || source.getItem().getCount() != 1
                || profile == null || tool == null || !toolAdmission.test(tool)) return null;
        var proof = new LooseItemProcessingProof(actor, authority, hands, request, source, source.getUUID(),
                source.position(), source.getItem().copy(), held.copy(), profile, tool, toolAdmission);
        return proof.current() ? proof : null;
    }

    /** Revalidates authority, live hand/tool/source identity and selected reachable line-of-sight target. */
    public boolean current() {
        var body = authority.body();
        var level = body.level() instanceof ServerLevel server ? server : null;
        if (level == null || !HandActorAuthority.revalidate(actor, authority) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands || !hands.compatible(body)
                || hands.revision() != request.expectedRevision() || !request.handId().equals(hands.activeHand())
                || !Objects.equals(hands.token(request.handId()).orElse(null), request.expectedToken())
                || hands.stackCopy(request.handId()).filter(s -> ItemStack.isSameItemSameComponents(s, heldStack)
                        && s.getCount() == heldStack.getCount()).isEmpty()
                || source.level() != level || source.isRemoved() || !source.isAddedToLevel()
                || level.getEntity(sourceId) != source || !origin.equals(source.position())
                || !ItemStack.isSameItemSameComponents(sourceStack, source.getItem())
                || sourceStack.getCount() != source.getItem().getCount()) return false;
        var sourceItemId = BuiltInRegistries.ITEM.getKey(source.getItem().getItem());
        var toolId = BuiltInRegistries.ITEM.getKey(heldStack.getItem());
        var processing = ModItemProcessingProfiles.serverCatalog();
        var tools = PrototypeRuntime.serverManager().snapshot(ModToolProfiles.TYPE);
        if (processing == null || processing.get(sourceItemId) != profile || toolId == null
                || tools.get(toolId) != toolProfile || !toolAdmission.test(toolProfile)) return false;
        var selected = BodyItemTargetProbe.probeSelected(level, body, sourceId, BodyItemTargetProbe.MAX_REACH);
        return selected != null && HandActorAuthority.revalidate(actor, authority)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && hands.revision() == request.expectedRevision() && !source.isRemoved()
                && level.getEntity(sourceId) == source && origin.equals(source.position());
    }
}
