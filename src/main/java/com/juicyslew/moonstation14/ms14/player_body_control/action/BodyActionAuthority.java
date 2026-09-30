package com.juicyslew.moonstation14.ms14.player_body_control.action;

import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.interaction.ComplexInteractionSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Optional;

/** Read-only server BODY actor lookup, not an action implementation or a capability grant. */
public final class BodyActionAuthority {
    private BodyActionAuthority() { }

    public record Snapshot(ServerPlayer player, LivingEntity body, BodyActionPolicy.Identity identity) { }

    public static Optional<Snapshot> resolve(ServerPlayer player) {
        if (!validPlayer(player)) return Optional.empty();
        var lifecycle = LifecycleCharacterSessionControl.activeCharacterBody(player);
        var experimental = GhostMobHarnessControl.activeHarness(player);
        if (!BodyActionPolicy.selects(lifecycle.isPresent(), experimental.isPresent(),
                experimental.filter(active -> active.kind() == MobHarnessKind.CHARACTER).isPresent()))
            return Optional.empty();

        LivingEntity body;
        BodyActionPolicy.Source source;
        com.juicyslew.moonstation14.ms14.player_body_control.MindId mind;
        com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId harness;
        long epoch;
        if (lifecycle.isPresent()) {
            var active = lifecycle.orElseThrow();
            body = active.body();
            source = BodyActionPolicy.Source.LIFECYCLE;
            mind = active.mindId();
            harness = active.harnessId();
            epoch = active.epoch();
        } else {
            var active = experimental.orElseThrow();
            body = active.entity();
            source = BodyActionPolicy.Source.EXPERIMENTAL_HARNESS;
            mind = active.mindId();
            harness = active.harnessId();
            epoch = active.epoch();
        }
        if (body == null || mind == null || harness == null || epoch <= 0 || !validBody(player, body)
                || !harness.value().equals(body.getUUID())) return Optional.empty();
        var prototype = CharacterIdentitySystem.resolve(body);
        if (prototype.isEmpty()) return Optional.empty();
        var identity = new BodyActionPolicy.Identity(player, body, body.getUUID(), source,
                mind, harness, epoch, prototype.orElseThrow(), ComplexInteractionSystem.enabled(body));
        return Optional.of(new Snapshot(player, body, identity));
    }

    /** Re-resolve controllers, prototype, and live component immediately before inspecting a snapshot. */
    public static boolean revalidate(ServerPlayer player, Snapshot expected) {
        return expected != null && expected.player() == player
                && resolve(player).filter(current -> BodyActionPolicy.same(expected.identity(), current.identity())).isPresent();
    }

    /** Read-only status check after revalidation; does not authorize or perform a world action. */
    public static boolean eligibleForComplexInteraction(ServerPlayer player, Snapshot expected) {
        return revalidate(player, expected) && BodyActionPolicy.eligibleForComplexInteraction(expected.identity(),
                CharacterControlSystem.isStunned(expected.body()));
    }

    private static boolean validPlayer(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level) || level.isClientSide) return false;
        MinecraftServer server = level.getServer();
        return server != null && BodyActionPolicy.eligiblePlayer(server.isSameThread(),
                server.getPlayerList().getPlayer(player.getUUID()) == player,
                !(player instanceof FakePlayer), player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR,
                !player.isRemoved() && player.isAlive(), !player.isPassenger());
    }

    private static boolean validBody(ServerPlayer player, LivingEntity body) {
        return body.level() == player.level() && player.getCamera() == body
                && body.level() instanceof ServerLevel level && !body.isRemoved() && body.isAlive()
                && body.isAddedToLevel() && !body.isPassenger() && level.getEntity(body.getUUID()) == body;
    }
}
