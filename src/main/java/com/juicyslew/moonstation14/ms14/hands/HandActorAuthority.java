package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only admission view of the exact currently controlled CHARACTER body for hand actions. */
public final class HandActorAuthority {
    private HandActorAuthority() { }

    public enum Source { LIFECYCLE, EXPERIMENTAL_HARNESS }

    /** Immutable point-in-time admission snapshot. Revalidate it immediately before any future item commit. */
    public record Snapshot(ServerPlayer actor, LivingEntity body, Source source, MindId mindId,
                           MobHarnessId harnessId, long epoch, List<String> handIds) {
        public Snapshot {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(mindId, "mindId");
            Objects.requireNonNull(harnessId, "harnessId");
            handIds = List.copyOf(handIds);
            if (epoch <= 0 || handIds.isEmpty()) throw new IllegalArgumentException("Invalid hand actor snapshot");
        }
    }

    /** Resolves exactly one authority's eligible, authenticated CHARACTER body; never materializes state. */
    public static Optional<Snapshot> resolve(ServerPlayer actor) {
        if (!validActor(actor)) return Optional.empty();

        var lifecycle = LifecycleCharacterSessionControl.activeCharacterBody(actor);
        var experimental = GhostMobHarnessControl.activeHarness(actor);
        if (!acceptsExactlyOneAuthority(lifecycle.isPresent(), experimental.isPresent())) return Optional.empty();

        LivingEntity body;
        Source source;
        MindId mindId;
        MobHarnessId harnessId;
        long epoch;
        if (lifecycle.isPresent()) {
            var active = lifecycle.orElseThrow();
            body = active.body();
            source = Source.LIFECYCLE;
            mindId = active.mindId();
            harnessId = active.harnessId();
            epoch = active.epoch();
        } else {
            var active = experimental.orElseThrow();
            if (active.kind() != MobHarnessKind.CHARACTER) return Optional.empty();
            body = active.entity();
            source = Source.EXPERIMENTAL_HARNESS;
            mindId = active.mindId();
            harnessId = active.harnessId();
            epoch = active.epoch();
        }

        if (mindId == null || harnessId == null || epoch <= 0 || !validBody(actor, body)
                || !actor.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())
                || !CarrierHandInventoryGate.allows(actor)
                || !harnessId.value().equals(body.getUUID())) return Optional.empty();
        var capability = HandCapability.resolve(body);
        return capability.filter(ids -> compatiblePersisted(
                        body.hasData(ModDataAttachments.HANDS.get()),
                        body.getExistingDataOrNull(ModDataAttachments.HANDS.get()), ids))
                .map(ids -> new Snapshot(actor, body, source, mindId, harnessId, epoch, ids));
    }

    /** Re-resolves both authorities and capability; false for any stale, ghost, Creative, or uncapable state. */
    public static boolean revalidate(ServerPlayer actor, Snapshot snapshot) {
        if (snapshot == null || actor != snapshot.actor()) return false;
        return resolve(actor).filter(current -> sameResolvedActor(snapshot, current)).isPresent();
    }

    static boolean acceptsExactlyOneAuthority(boolean lifecyclePresent, boolean experimentalPresent) {
        return lifecyclePresent != experimentalPresent;
    }

    static boolean sameResolvedActor(Snapshot expected, Snapshot current) {
        return expected != null && current != null
                && expected.actor() == current.actor()
                && expected.body() == current.body()
                && expected.body().getUUID().equals(current.body().getUUID())
                && expected.source() == current.source()
                && expected.mindId().equals(current.mindId())
                && expected.harnessId().equals(current.harnessId())
                && expected.epoch() == current.epoch()
                && sameCapabilityIds(expected.handIds(), current.handIds());
    }

    static boolean sameCapabilityIds(List<String> expected, List<String> current) {
        return expected != null && current != null && !expected.isEmpty() && expected.equals(current);
    }

    /** No persisted state is acceptable; present state must agree exactly, including order. */
    static boolean compatiblePersisted(boolean present, HandAttachment attachment, List<String> handIds) {
        return !present || attachment != null && HandCapability.isCompatible(attachment.toComponent(), handIds);
    }

    private static boolean validActor(ServerPlayer actor) {
        if (actor == null || actor instanceof FakePlayer || !(actor.level() instanceof ServerLevel level)
                || level.isClientSide || !actor.isAlive() || actor.isRemoved() || actor.isPassenger()
                || actor.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) return false;
        MinecraftServer server = level.getServer();
        return server != null && server.isSameThread()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }

    private static boolean validBody(ServerPlayer actor, LivingEntity body) {
        if (body == null || body instanceof ServerPlayer || body.level() != actor.level()
                || body.isRemoved() || !body.isAlive()
                || !body.isAddedToLevel() || body.isPassenger()
                || !(body.level() instanceof ServerLevel level)) return false;
        return level.getEntity(body.getUUID()) == body;
    }
}
