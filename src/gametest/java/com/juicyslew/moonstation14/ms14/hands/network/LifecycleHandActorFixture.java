package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Real first-join lifecycle authority on a NeoForge-compatible embedded server connection. */
public final class LifecycleHandActorFixture {
    private LifecycleHandActorFixture() { }

    public record Ready(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                         EmbeddedChannel channel, long epoch, AtomicLong intentSequence, AtomicLong appliedSequence,
                          java.util.concurrent.atomic.AtomicReference<java.util.function.Supplier<net.minecraft.world.phys.Vec3>> requestedView,
                          java.util.concurrent.atomic.AtomicInteger wishX, java.util.concurrent.atomic.AtomicInteger wishZ,
                          java.util.concurrent.atomic.AtomicInteger buttons,
                           java.util.concurrent.atomic.AtomicReference<GhostControlPayloads.Snapshot> latestSnapshot,
                           java.util.concurrent.atomic.AtomicReference<java.util.Optional<GhostControlPayloads.SupportedMovementReference>> supportedMovementReference) {
        /** Select the real quantized lifecycle control wish sent by the normal per-tick fixture pump. */
        public void setWish(int x, int z) {
            if (Math.abs(x) > 1000 || Math.abs(z) > 1000)
                throw new IllegalArgumentException("fixture wish axes must be in [-1000,1000]");
            wishX.set(x);
            wishZ.set(z);
        }
        /** Set only the native payload sprint bit for controlled fixture movement. */
        public void setSprint(boolean sprint) {
            buttons.updateAndGet(value -> sprint ? value | GhostControlPayloads.BUTTON_SPRINT
                    : value & ~GhostControlPayloads.BUTTON_SPRINT);
        }
        /** Hold an optional support-relative heading through the normal lifecycle input pump. */
        public void setSupportedMovementReference(java.util.Optional<GhostControlPayloads.SupportedMovementReference> reference) {
            supportedMovementReference.set(java.util.Objects.requireNonNull(reference, "reference"));
        }
        /** Feed real standing view input through the registered lifecycle payload route. */
        public long viewTo(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.phys.Vec3 point) {
            requestedView.set(() -> point);
            return neutralIntent(body);
        }

        public void trackViewToward(net.minecraft.world.entity.LivingEntity body,
                                    java.util.function.Supplier<net.minecraft.world.phys.Vec3> point) {
            requestedView.set(point);
            neutralIntent(body);
        }

        private long sequenceTo(net.minecraft.world.entity.LivingEntity body, net.minecraft.world.phys.Vec3 point) {
            var delta = point.subtract(body.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(delta.y,
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
            long sequence = intentSequence.incrementAndGet();
            var intent = new GhostControlPayloads.Intent(epoch, sequence,
                    (short) wishX.get(), (short) wishZ.get(), (byte) 0, buttons.get(), yaw, pitch,
                    supportedMovementReference.get());
            NetworkRegistry.handleModdedPayload(actor.connection, new ServerboundCustomPayloadPacket(intent));
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            return sequence;
        }

        public void dispatch(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
            NetworkRegistry.handleModdedPayload(actor.connection, new ServerboundCustomPayloadPacket(payload));
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
        }

        public long neutralIntent(net.minecraft.world.entity.LivingEntity body) {
            if (intentSequence.get() < Long.MAX_VALUE - 1) {
                var supplier = requestedView.get();
                var target = supplier == null ? null : supplier.get();
                if (target != null) return sequenceTo(body, target);
                long sequence = intentSequence.incrementAndGet();
                var intent = new GhostControlPayloads.Intent(epoch, sequence,
                        (short) wishX.get(), (short) wishZ.get(), (byte) 0, buttons.get(), body.getYRot(), body.getXRot(),
                        supportedMovementReference.get());
                dispatch(intent);
                return sequence;
            }
            return intentSequence.get();
        }

        /** Largest real lifecycle acknowledgement observed on the mock server-to-client transport. */
        public long drainAppliedSequence() {
            for (GhostControlPayloads.Snapshot snapshot : drainSnapshots())
                appliedSequence.accumulateAndGet(snapshot.acknowledgedAppliedSequence(), Math::max);
            return appliedSequence.get();
        }

        public java.util.List<GhostControlPayloads.Snapshot> drainSnapshots() {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            java.util.ArrayList<GhostControlPayloads.Snapshot> snapshots = new java.util.ArrayList<>();
            Object value;
            while ((value = channel.readOutbound()) != null)
                if (value instanceof ClientboundCustomPayloadPacket packet
                        && packet.payload() instanceof GhostControlPayloads.Snapshot snapshot
                        && snapshot.epoch() == epoch) {
                    latestSnapshot.set(snapshot);
                    snapshots.add(snapshot);
                }
            return java.util.List.copyOf(snapshots);
        }

        public String drainBlockUseOutbounds(long requestSequence) {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            BodyBlockUseResult found = null;
            StringBuilder seen = new StringBuilder();
            int otherPackets = 0;
            Object value;
            while ((value = channel.readOutbound()) != null) {
                if (value instanceof ClientboundCustomPayloadPacket packet
                        && packet.payload() instanceof BodyBlockUseResult result
                        && result.sequence() == requestSequence) found = result;
                else if (value instanceof ClientboundCustomPayloadPacket packet
                        && packet.payload() instanceof BodyBlockUseResult result) {
                    if (otherPackets++ < 3) {
                        if (!seen.isEmpty()) seen.append(" | ");
                        seen.append("otherBodyResult=").append(result);
                    }
                } else if (otherPackets++ < 3) {
                    if (!seen.isEmpty()) seen.append(" | ");
                    seen.append(value.getClass().getSimpleName());
                }
            }
            return "result=" + found + ", otherPackets=" + otherPackets + (seen.isEmpty() ? "" : ", sample=" + seen);
        }

        /** Polls asynchronously handled block-use responses, retaining the exact epoch/sequence match. */
        public BodyBlockUseResult pollBlockUseResult(long requestEpoch, long requestSequence,
                                                      java.util.List<String> payloadEvidence) {
            channel.runPendingTasks();
            channel.runScheduledPendingTasks();
            BodyBlockUseResult match = null;
            Object value;
            while ((value = channel.readOutbound()) != null) {
                if (value instanceof ClientboundCustomPayloadPacket packet) {
                    var payload = packet.payload();
                    String evidence = payload.getClass().getSimpleName() + "[" + payload.type().id() + "]";
                    if (payload instanceof BodyBlockUseResult result) {
                        evidence += "(epoch=" + result.epoch() + ",seq=" + result.sequence()
                                + ",outcome=" + result.outcome() + ")";
                        if (result.epoch() == requestEpoch && result.sequence() == requestSequence)
                            match = result;
                    }
                    if (payload instanceof BodyBlockUseResult result && result.epoch() == requestEpoch
                            && result.sequence() == requestSequence)
                        retainMatchedEvidence(payloadEvidence, evidence);
                    else addPayloadEvidence(payloadEvidence, evidence);
                } else {
                    addPayloadEvidence(payloadEvidence, value.getClass().getSimpleName());
                }
            }
            return match;
        }

        private static void addPayloadEvidence(java.util.List<String> evidence, String value) {
            if (evidence.size() < 6) evidence.add(value.length() <= 72 ? value : value.substring(0, 69) + "...");
        }

        private static void retainMatchedEvidence(java.util.List<String> evidence, String value) {
            String shortValue = value.length() <= 72 ? value : value.substring(0, 69) + "...";
            if (evidence.size() < 6) evidence.add(shortValue);
            else evidence.set(5, shortValue);
        }
    }

    public static void start(GameTestHelper helper, String name, Consumer<Ready> callback) {
        start(helper, name, 0, callback);
    }

    /** First join that deliberately withholds Ready for a bounded number of real server ticks. */
    public static void start(GameTestHelper helper, String name, int delayReadyTicks, Consumer<Ready> callback) {
        if (delayReadyTicks < 0 || delayReadyTicks > 300) throw new IllegalArgumentException("invalid Ready delay");
        var startup = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime
                .snapshot(helper.getLevel().getServer()).orElse(null);
        if (startup == null || (startup.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime.State.EMPTY
                && startup.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime.State.UNINITIALIZED))
            throw new GameTestAssertException("first-join fixture requires a fresh disposable lifecycle save; state="
                    + (startup == null ? "missing" : startup.state()) + ", profiles="
                    + (startup == null ? "unknown" : startup.profileCount()));
        UUID uuid = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, name), false);
        ServerPlayer actor = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override public void loadGameTypes(CompoundTag saved) {
                CompoundTag mode = saved == null ? new CompoundTag() : saved.copy();
                mode.putInt("playerGameType", GameType.SURVIVAL.getId());
                super.loadGameTypes(mode);
                if (gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
                    throw new GameTestAssertException("fixture player data did not establish Survival before login");
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        var playerList = helper.getLevel().getServer().getPlayerList();
        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            helper.getLevel().getServer().getConnection().getConnections().remove(connection);
            if (playerList.getPlayer(uuid) == actor) playerList.remove(actor);
            channel.finishAndReleaseAll();
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                                                    net.minecraft.gametest.framework.GameTestInfo newTest,
                                                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
        });
        // placeNewPlayer does not enroll embedded GameTest channels in the server's normal connection tick list.
        // Register this exact mock transport so the ordinary ServerConnectionListener -> player.doTick lifecycle runs.
        helper.getLevel().getServer().getConnection().getConnections().add(connection);
        playerList.placeNewPlayer(connection, actor, cookie);
        if (actor.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
            throw new GameTestAssertException("normal Survival first join did not enter lifecycle spectator control");
        GhostControlPayloads.Begin[] begin = new GhostControlPayloads.Begin[1];
        BoundedGameTestWait.await(helper, 50, () -> "outbound lifecycle Begin unavailable; actor=" + uuid,
                () -> (begin[0] = outbound(channel, GhostControlPayloads.Begin.class)) != null,
                () -> {
                    if (begin[0].harnessKind() != MobHarnessKind.CHARACTER) {
                        cleanup.run(); helper.fail("lifecycle first join emitted a non-character Begin"); return;
                    }
                    long epoch = begin[0].mindEpoch();
                    int bodyEntity = begin[0].harnessEntityId();
                    net.minecraft.world.entity.Entity expectedBody = helper.getLevel().getEntity(bodyEntity);
                    if (expectedBody == null) {
                        cleanup.run(); helper.fail("lifecycle Begin named an entity absent from the original world"); return;
                    }
                    Runnable sendReady = () -> NetworkRegistry.handleModdedPayload(actor.connection,
                            new ServerboundCustomPayloadPacket(new GhostControlPayloads.Ready(epoch)));
                    Runnable awaitCommittedAuthority = () -> BoundedGameTestWait.await(helper, 50,
                            () -> "lifecycle Ready did not resolve authority and issue owner hands actor=" + uuid,
                            () -> {
                                var authority = HandActorAuthority.resolve(actor).orElse(null);
                                if (authority == null) return false;
                                if (authority.source() != HandActorAuthority.Source.LIFECYCLE || authority.epoch() != epoch
                                        || authority.body().getId() != bodyEntity || !authority.actor().getUUID().equals(uuid))
                                    throw new GameTestAssertException("resolved authority differs from lifecycle Begin");
                                if (!(authority.body() instanceof PlayerCharacterHarnessEntity body)
                                        || body.playerCharacterBinding() == null
                                        || !body.playerCharacterBinding().accountId().equals(uuid)
                                        || !body.playerCharacterBinding().mindId().equals(authority.mindId().value())
                                        || !"main".equals(body.playerCharacterBinding().profileKey())
                                        || body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()) == null
                                        || !ModCharacters.HUMAN_ID.equals(body.getExistingDataOrNull(
                                                ModDataAttachments.CHARACTER_IDENTITY.get()).characterId()))
                                    throw new GameTestAssertException("lifecycle body lacks first-join account/profile/Mind/HUMAN identity");
                                var park = actor.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
                                if (park == null || !park.account().equals(uuid))
                                    throw new GameTestAssertException("lifecycle carrier park does not belong to actor");
                                return BodyHandStateService.hasIssued(actor, authority);
                            }, () -> {
                                var authority = HandActorAuthority.resolve(actor).orElseThrow();
                                AtomicLong sequences = new AtomicLong();
                                AtomicLong applied = new AtomicLong(-1);
                                Ready ready = new Ready(actor, authority, channel, epoch, sequences, applied,
                                        new java.util.concurrent.atomic.AtomicReference<>(),
                                         new java.util.concurrent.atomic.AtomicInteger(),
                                         new java.util.concurrent.atomic.AtomicInteger(),
                                         new java.util.concurrent.atomic.AtomicInteger(),
                                          new java.util.concurrent.atomic.AtomicReference<>(),
                                          new java.util.concurrent.atomic.AtomicReference<>(java.util.Optional.empty()));
                                MoonStation14.LOGGER.info("[authority fixture] headless mock actor={} body={} mind={} epoch={} handIds={}",
                                        uuid, authority.body().getUUID(), authority.mindId(), epoch, authority.handIds());
                                callback.accept(ready);
                            });
                    if (delayReadyTicks == 0) {
                        sendReady.run();
                        awaitCommittedAuthority.run();
                    } else {
                        long delayStarted = helper.getLevel().getGameTime();
                        BoundedGameTestWait.await(helper, delayReadyTicks + 40,
                                () -> "healthy delayed Ready owner disconnected or changed identity actor=" + uuid,
                                () -> helper.getLevel().getGameTime() - delayStarted >= delayReadyTicks,
                                () -> {
                                    if (!actor.connection.isAcceptingMessages() || playerList.getPlayer(uuid) != actor
                                            || actor.isRemoved() || helper.getLevel().getEntity(bodyEntity) != expectedBody) {
                                        cleanup.run(); helper.fail("healthy Ready delay lost exact connection/body"); return;
                                    }
                                    GhostControlPayloads.Begin retry = outbound(channel, GhostControlPayloads.Begin.class);
                                    if (retry != null && (retry.mindEpoch() != epoch || retry.harnessEntityId() != bodyEntity)) {
                                        cleanup.run(); helper.fail("late Ready retry changed the original epoch/body identity"); return;
                                    }
                                    sendReady.run();
                                    awaitCommittedAuthority.run();
                                });
                    }
                });
    }

    /** Normal PlayerList login for a cleanly logged-out fixture identity; no registry/profile injection. */
    public static void reconnect(GameTestHelper helper, Ready previous, Consumer<Ready> callback) {
        UUID uuid = previous.actor().getUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid,
                previous.actor().getGameProfile().getName()), false);
        ServerPlayer actor = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override public void loadGameTypes(CompoundTag saved) {
                CompoundTag mode = saved == null ? new CompoundTag() : saved.copy();
                mode.putInt("playerGameType", GameType.SURVIVAL.getId());
                super.loadGameTypes(mode);
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        var playerList = helper.getLevel().getServer().getPlayerList();
        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            helper.getLevel().getServer().getConnection().getConnections().remove(connection);
            if (playerList.getPlayer(uuid) == actor) playerList.remove(actor);
            channel.finishAndReleaseAll();
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                                                    net.minecraft.gametest.framework.GameTestInfo newTest,
                                                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
        });
        helper.getLevel().getServer().getConnection().getConnections().add(connection);
        playerList.placeNewPlayer(connection, actor, cookie);
        completeReconnect(helper, actor, channel, previous.authority().body().getUUID(),
                previous.authority().mindId(), previous.authority().handIds(),
                () -> "normal reconnect for " + uuid, callback);
    }

    /** Normal PlayerList admission without requiring Begin, for deliberate safe-waiting reconnect tests. */
    public static void reconnectCarrier(GameTestHelper helper, Ready previous,
                                        java.util.function.BiConsumer<ServerPlayer, EmbeddedChannel> afterLogin) {
        UUID uuid = previous.actor().getUUID();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid,
                previous.actor().getGameProfile().getName()), false);
        ServerPlayer actor = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                cookie.gameProfile(), cookie.clientInformation()) {
            @Override public void loadGameTypes(CompoundTag saved) {
                CompoundTag mode = saved == null ? new CompoundTag() : saved.copy();
                mode.putInt("playerGameType", GameType.SURVIVAL.getId());
                super.loadGameTypes(mode);
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        var server = helper.getLevel().getServer();
        var playerList = server.getPlayerList();
        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            server.getConnection().getConnections().remove(connection);
            if (playerList.getPlayer(uuid) == actor) playerList.remove(actor);
            channel.finishAndReleaseAll();
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test,
                                             net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,
                    net.minecraft.gametest.framework.GameTestInfo newTest,
                    net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
        });
        server.getConnection().getConnections().add(connection);
        playerList.placeNewPlayer(connection, actor, cookie);
        if (playerList.getPlayer(uuid) != actor) {
            cleanup.run(); helper.fail("deferred reconnect was not admitted to PlayerList"); return;
        }
        afterLogin.accept(actor, channel);
    }

    /** The shared bounded Begin/Ready flow for ordinary and explicitly retried reconnects. */
    public static void completeReconnect(GameTestHelper helper, ServerPlayer actor, EmbeddedChannel channel,
            UUID expectedBody, com.juicyslew.moonstation14.ms14.player_body_control.MindId expectedMind,
            java.util.List<String> expectedHands, java.util.function.Supplier<String> beginDiagnostic,
            Consumer<Ready> callback) {
        GhostControlPayloads.Begin[] begin = new GhostControlPayloads.Begin[1];
        BoundedGameTestWait.await(helper, 50, () -> beginDiagnostic.get() + "; waiting for Ready owner hands issuance",
                () -> (begin[0] = outbound(channel, GhostControlPayloads.Begin.class)) != null,
                () -> {
                    Entity offered = begin[0] == null ? null : helper.getLevel().getEntity(begin[0].harnessEntityId());
                    if (begin[0] == null || begin[0].harnessKind() != MobHarnessKind.CHARACTER
                            || offered == null || !offered.getUUID().equals(expectedBody)) {
                        helper.fail("reconnect Begin did not offer the exact existing Character body"); return;
                    }
                    long epoch = begin[0].mindEpoch();
                    NetworkRegistry.handleModdedPayload(actor.connection,
                            new ServerboundCustomPayloadPacket(new GhostControlPayloads.Ready(epoch)));
                    channel.runPendingTasks(); channel.runScheduledPendingTasks();
                    BoundedGameTestWait.await(helper, 50, () -> "reconnect Ready did not commit and issue owner hands body=" + expectedBody,
                             () -> HandActorAuthority.resolve(actor).map(authority ->
                                     authority.source() == HandActorAuthority.Source.LIFECYCLE
                                             && authority.actor() == actor && authority.body().getUUID().equals(expectedBody)
                                             && authority.mindId().equals(expectedMind)
                                             && authority.handIds().equals(expectedHands)
                                             && BodyHandStateService.hasIssued(actor, authority)).orElse(false),
                            () -> {
                                var authority = HandActorAuthority.resolve(actor).orElseThrow();
                                callback.accept(new Ready(actor, authority, channel, epoch, new AtomicLong(),
                                        new AtomicLong(-1), new java.util.concurrent.atomic.AtomicReference<>(),
                                        new java.util.concurrent.atomic.AtomicInteger(),
                                        new java.util.concurrent.atomic.AtomicInteger(),
                                        new java.util.concurrent.atomic.AtomicInteger(),
                                         new java.util.concurrent.atomic.AtomicReference<>(),
                                         new java.util.concurrent.atomic.AtomicReference<>(java.util.Optional.empty())));
                            });
                });
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> T outbound(
            EmbeddedChannel channel, Class<T> type) {
        channel.runPendingTasks(); channel.runScheduledPendingTasks();
        T result = null;
        Object value;
        while ((value = channel.readOutbound()) != null)
            if (value instanceof ClientboundCustomPayloadPacket packet && type.isInstance(packet.payload()))
                result = type.cast(packet.payload());
        return result;
    }
}
