package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Explicit, owner-bound server API for changing a persistent character's bounded appearance. */
public final class PlayerCharacterAppearanceService {
    private PlayerCharacterAppearanceService() { }

    /**
     * The caller must obtain expectedGeneration from authoritative server lifecycle state; it is never request data.
     */
    public static boolean setAppearance(PlayerCharacterHarnessEntity body, ServerPlayer player,
                                        PlayerCharacterAppearance appearance, PlayerLifecycleRegistry lifecycle,
                                        long expectedGeneration) {
        if (appearance == null) return false;
        if (!authorizedToEdit(body, player, lifecycle, expectedGeneration)) return false;
        body.setAppearance(appearance);
        return true;
    }

    /** Changes only the independent wide/slim body geometry selection. */
    public static boolean setBodyShape(PlayerCharacterHarnessEntity body, ServerPlayer player,
                                       PlayerCharacterBodyShape shape, PlayerLifecycleRegistry lifecycle,
                                       long expectedGeneration) {
        if (shape == null) return false;
        if (!authorizedToEdit(body, player, lifecycle, expectedGeneration)) return false;
        body.setBodyShape(shape);
        return true;
    }

    private static boolean authorizedToEdit(PlayerCharacterHarnessEntity body, ServerPlayer player,
                                            PlayerLifecycleRegistry lifecycle, long expectedGeneration) {
        boolean gatesPermit = MindGhostStartupGate.enabledForServer() && !MovementStartupGate.enabledForServer();
        if (!gatesPermit) return false;
        if (body == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        if (player == null || lifecycle == null
                || body.isRemoved() || body.level().isClientSide
                || level.getEntity(body.getUUID()) != body || body.hasInvalidSavedBinding()
                || !isAuthenticatedPlayer(player, level)) return false;
        PlayerCharacterBinding binding = body.playerCharacterBinding();
        var profile = lifecycle.profile(player.getUUID()).orElse(null);
        MobHarnessId bodyId = new MobHarnessId(body.getUUID());
        if (!canEdit(gatesPermit, level.getServer().isSameThread(), binding, player.getUUID(),
                profile, bodyId, expectedGeneration)
                || !lifecycle.authorizes(player.getUUID(), expectedGeneration, bodyId,
                target -> target.kind() == MobHarnessKind.CHARACTER))
            return false;
        return true;
    }

    private static boolean isAuthenticatedPlayer(ServerPlayer player, ServerLevel bodyLevel) {
        return isAuthenticatedPlayerPolicy(!(player instanceof FakePlayer), !player.isRemoved(),
                !player.level().isClientSide, player.getServer() == bodyLevel.getServer(),
                player.level() == bodyLevel,
                player.connection != null && player.connection.isAcceptingMessages(),
                bodyLevel.getServer().getPlayerList().getPlayer(player.getUUID()) == player);
    }

    static boolean isAuthenticatedPlayerPolicy(boolean notFakePlayer, boolean live, boolean serverPlayer,
                                               boolean sameServer, boolean sameWorld, boolean connected,
                                               boolean exactPlayerListInstance) {
        return notFakePlayer && live && serverPlayer && sameServer && sameWorld && connected
                && exactPlayerListInstance;
    }

    static boolean canEdit(boolean gatesPermit, boolean serverThread, PlayerCharacterBinding binding,
                           UUID accountId, PlayerLifecycleRegistry.Snapshot profile,
                           MobHarnessId bodyId, long expectedGeneration) {
        return gatesPermit && serverThread && binding != null && accountId != null
                && profile != null && bodyId != null && binding.accountId().equals(accountId)
                && profile.accountId().equals(accountId)
                && profile.state() == PlayerLifecycleRegistry.LifecycleState.ACTIVE && profile.active()
                && profile.connectionGeneration() == expectedGeneration
                && profile.mindId().value().equals(binding.mindId())
                && profile.profileId().equals(binding.profileKey())
                && profile.bodyId().equals(bodyId);
    }
}
