package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.tool.ModTools;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Authoritative, owner-local ticking for finite welder fuel. */
public final class WelderOwnerTickService {
    private WelderOwnerTickService() { }

    /** Call from the server-side per-entity tick before filtering to living entities. */
    public static void tick(Entity entity, ServerLevel level) {
        if (entity == null || level == null || level.isClientSide || entity.level() != level || entity.isRemoved()
                || !entity.isAddedToLevel() || level.getEntity(entity.getUUID()) != entity
                || !level.hasChunkAt(entity.blockPosition())) return;
        ToolPrototypeData tool = welderPrototype();
        if (tool == null) return;
        long now = level.getGameTime();
        if (entity instanceof ItemEntity item) tickWorldItem(item, level, tool, now);
        if (entity instanceof LivingEntity body) tickBody(body, tool, now);
    }

    private static void tickWorldItem(ItemEntity item, ServerLevel level, ToolPrototypeData tool, long now) {
        ItemStack before = item.getItem();
        if (!before.is(ModItems.WELDER)) return;
        ItemStack after = WelderFuelService.tickOwnerStack(before, tool, now);
        if (after == before || ItemStack.isSameItemSameComponents(before, after)) return;
        if (item.isRemoved() || item.level() != level || level.isClientSide
                || level.getEntity(item.getUUID()) != item
                || !ItemStack.isSameItemSameComponents(item.getItem(), before)) return;
        item.setItem(after);
    }

    private static void tickBody(LivingEntity body, ToolPrototypeData tool, long now) {
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body)) return;
        LiveHands next = before.tickWelderStacks(stack -> WelderFuelService.tickOwnerStack(stack, tool, now));
        if (next == before || body.isRemoved() || body.level().isClientSide
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)) return;
        LiveHandsCommitGateway.commitOwned(body, next);
        LiveHands published = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (published != next || !published.compatible(body) || published.revision() != before.revision()) return;
    }

    private static ToolPrototypeData welderPrototype() {
        var manager = PrototypeRuntime.serverManager();
        if (!manager.registeredTypes().contains(ModTools.TOOL_TYPE)) return null;
        return manager.snapshot(ModTools.TOOL_TYPE).get(ModTools.WELDER);
    }
}

