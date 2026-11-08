package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Authoritative server-side /give admission into a controlled body's hands and physical world root. */
public final class BodyGiveService {
    private BodyGiveService() { }

    /** Authenticated command wrapper. Returns false when no current controlled body is admitted. */
    public static boolean give(ServerPlayer actor, ItemStack parsedStack) {
        return giveCommand(actor, parsedStack) == CommandResult.HANDLED;
    }

    public enum CommandResult { NOT_CONTROLLED, HANDLED, FAILED }

    /** Distinguishes normal vanilla fallback from a failed controlled grant, which must never carrier-fallback. */
    public static CommandResult giveCommand(ServerPlayer actor, ItemStack parsedStack) {
        BodyActionAuthority.Snapshot authority = BodyActionAuthority.resolve(actor).orElse(null);
        if (authority == null) return CommandResult.NOT_CONTROLLED;
        if (parsedStack == null || parsedStack.isEmpty()) return CommandResult.FAILED;
        ItemStack grant = parsedStack.copy();
        boolean handled = giveTrustedBody(authority.body(), grant,
                () -> BodyActionAuthority.revalidate(actor, authority)) && clearInput(parsedStack, grant);
        return handled ? CommandResult.HANDLED : CommandResult.FAILED;
    }

    /** Narrow trusted-server consumer, also used by native core fixtures after fixture-owned admission. */
    public static boolean giveTrustedBody(LivingEntity body, ItemStack grant, java.util.function.BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> giveTrustedBodyInBatch(body, grant, authorityValid));
    }

    private static boolean giveTrustedBodyInBatch(LivingEntity body, ItemStack grant,
                                                    java.util.function.BooleanSupplier authorityValid) {
        if (body == null || grant == null || grant.isEmpty() || authorityValid == null
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.getServer().isSameThread() || body.isRemoved() || !body.isAddedToLevel()
                || level.getEntity(body.getUUID()) != body || !authorityValid.getAsBoolean()) return false;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands.GrantResult result = null;
        boolean hasUsableHands = before != null && before.compatible(body);
        if (hasUsableHands) {
            result = before.grant(before.revision(), grant);
            if (result.changed() && !BodyHandStateService.representable(result.state(), body)) {
                result = null;
            }
        }
        ItemStack remainder = result == null ? grant.copy() : result.remainder();
        boolean worldNeeded = !remainder.isEmpty();
        var worldEntity = worldNeeded ? BodyHandItemTransfer.spawnTrustedGrant(body, remainder) : null;
        if (worldNeeded && worldEntity == null) return false;
        if (!authorityValid.getAsBoolean() || body.isRemoved() || body.level() != level
                || level.getEntity(body.getUUID()) != body) {
            if (worldEntity != null && worldEntity.isAddedToLevel() && !worldEntity.isRemoved()) worldEntity.discard();
            return false;
        }
        if (result != null && result.changed()) {
            try { LiveHandsCommitGateway.commitOwned(body, before, result.state()); }
            catch (RuntimeException failure) {
                // The gateway may have published before a downstream callback failed. Only compensate
                // while this exact candidate is still the body's current value.
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == result.state()) {
                    try { LiveHandsCommitGateway.commitTrusted(body, result.state(), before); }
                    catch (RuntimeException ignored) { /* retain both owners if exact rollback is unavailable */ }
                }
                boolean restored = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before;
                if (!restored) return false;
                if (worldEntity != null && worldEntity.isAddedToLevel() && !worldEntity.isRemoved()) worldEntity.discard();
                return false;
            }
        }
        grant.setCount(0);
        return true;
    }

    private static boolean clearInput(ItemStack input, ItemStack grant) {
        input.setCount(0);
        return grant.isEmpty();
    }
}

