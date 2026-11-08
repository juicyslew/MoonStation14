package com.juicyslew.moonstation14.gametest.support;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;
import java.util.function.Consumer;

/** Socket-free native ServerPlayer fixtures for either ordinary PlayerList login or unenrolled world admission. */
public final class RegisteredServerPlayerFixture implements AutoCloseable {
    private final PlayerList playerList;
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private boolean closed;
    private final Connection connection;
    private final ServerLevel level;
    private final boolean playerListRegistered;
    private long handQuerySequence;
    private Consumer<Packet<?>> outboundObserver;
    private boolean acknowledgeChunkBatches;
    private long chunkBatchFinishAttempts;
    private long chunkBatchFinishReceipts;
    private long chunkBatchAcknowledgements;

    public record ChunkBatchProgress(long sent, long received, long acknowledged) { }

    private RegisteredServerPlayerFixture(PlayerList playerList, ServerLevel level, ServerPlayer player,
                                          Connection connection, EmbeddedChannel channel, boolean playerListRegistered) {
        this.playerList = playerList;
        this.level = level;
        this.player = player;
        this.connection = connection;
        this.channel = channel;
        this.playerListRegistered = playerListRegistered;
    }

    public static RegisteredServerPlayerFixture register(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        GameProfile profile = fixtureProfile(name);
        // GameTestServer's level settings default new players to Creative. PlayerList invokes
        // loadGameTypes(savedData) during its ordinary login path, so supply the fixture account's
        // vanilla persisted mode through that public join seam without altering global server mode.
        ServerPlayer player = new ServerPlayer(server, level, profile,
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public void loadGameTypes(CompoundTag savedData) {
                CompoundTag fixtureData = savedData == null ? new CompoundTag() : savedData.copy();
                fixtureData.putInt("playerGameType", GameType.SURVIVAL.getId());
                super.loadGameTypes(fixtureData);
            }
        };
        FixtureTransport transport = fixtureTransport();
        Connection connection = transport.connection();
        EmbeddedChannel channel = transport.channel();
        RegisteredServerPlayerFixture fixture = new RegisteredServerPlayerFixture(server.getPlayerList(), level,
                player, connection, channel, true);
        try {
            // This remains a PlayerList login fixture; the mock channel is initialized above.
            var cookie = new CommonListenerCookie(profile, 0,
                    net.minecraft.server.level.ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            fixture.playerList.placeNewPlayer(connection, player, cookie);
            fixture.installObservedListener(server, profile, cookie);
            return fixture;
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    /**
     * Creates an ordinary native ServerPlayer admitted to the level with a live packet connection,
     * but deliberately does not invoke PlayerList login/registration or lifecycle admission hooks.
     * This is a test seam for native entity behavior, not evidence of an authenticated player session.
     */
    public static RegisteredServerPlayerFixture createUnenrolled(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        GameProfile profile = fixtureProfile(name);
        ServerPlayer player = new ServerPlayer(server, level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        FixtureTransport transport = fixtureTransport();
        Connection connection = transport.connection();
        EmbeddedChannel channel = transport.channel();
        RegisteredServerPlayerFixture fixture = new RegisteredServerPlayerFixture(
                server.getPlayerList(), level, player, connection, channel, false);
        try {
            var cookie = new CommonListenerCookie(profile, 0,
                    net.minecraft.server.level.ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            fixture.installObservedListener(server, profile, cookie);
            // Establish the intended ordinary mode before level admission. No lifecycle login hook
            // has run for this intentionally unenrolled actor.
            player.setGameMode(GameType.SURVIVAL);
            if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL)
                throw new IllegalStateException("Unenrolled player fixture did not enter survival");
            level.addNewPlayer(player);
            return fixture;
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static GameProfile fixtureProfile(String name) {
        UUID id = UUID.randomUUID();
        // Profile names are limited to 16 characters; the suffix avoids world-local collisions.
        String suffix = id.toString().replace("-", "").substring(0, 8);
        return new GameProfile(id, (name.length() > 7 ? name.substring(0, 7) : name) + suffix);
    }

    private static FixtureTransport fixtureTransport() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // channelActive binds the real Connection to the embedded channel; use NeoForge's test
        // hook for the same negotiated mock peer setup as the PlayerList-backed fixture.
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        return new FixtureTransport(connection, channel);
    }

    private record FixtureTransport(Connection connection, EmbeddedChannel channel) { }

    public ServerPlayer player() { return player; }
    public Connection connection() { return connection; }
    public ChunkBatchProgress chunkBatchProgress() {
        return new ChunkBatchProgress(chunkBatchFinishAttempts, chunkBatchFinishReceipts, chunkBatchAcknowledgements);
    }

    /** Installs a scoped synchronous observer for packets sent by this fixture's real play listener. */
    public void observeOutbound(Consumer<Packet<?>> observer) {
        if (closed) throw new IllegalStateException("registered player fixture is closed");
        outboundObserver = observer;
    }

    private void installObservedListener(MinecraftServer server, GameProfile profile, CommonListenerCookie cookie) {
        var listener = new ServerGamePacketListenerImpl(server, connection, player, cookie) {
            @Override
            public void send(Packet<?> packet, PacketSendListener sendListener) {
                if (packet instanceof ClientboundChunkBatchFinishedPacket) chunkBatchFinishAttempts++;
                var observer = outboundObserver;
                if (observer != null) observer.accept(packet);
                super.send(packet, sendListener);
            }
        };
        player.connection = listener;
        connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(server.registryAccess(), listener.getConnectionType())), listener);
    }

    /** Issues the next ordinary hand-state query for this fixture's current body epoch. */
    public com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot queryHands(long epoch) {
        long sequence = ++handQuerySequence;
        var snapshot = com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService.query(player,
                new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery(epoch, sequence));
        if (snapshot.reason() != com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Reason.OK)
            throw new IllegalStateException("ordinary fixture hand query denied: reason=" + snapshot.reason()
                    + " epoch=" + epoch + " sequence=" + sequence);
        return snapshot;
    }

    /** Advances this socket-free connection's ordinary serverbound play listener once. */
    public void tickConnection() {
        if (closed) throw new IllegalStateException("registered player fixture is closed");
        connection.tick();
    }

    /** Enables ordinary client feedback for completed server chunk batches on this embedded connection. */
    public void enableChunkBatchAcknowledgements() {
        if (closed) throw new IllegalStateException("registered player fixture is closed");
        acknowledgeChunkBatches = true;
    }

    /** Outbound login/lifecycle packets remain available for a fixture's client-side Begin/Ready driver. */
    public Object pollOutbound() {
        // PacketDistributor/Connection may defer writes on the embedded event loop. Advance only
        // this fixture channel so ordinary outbound payload assertions observe completed writes.
        channel.runPendingTasks();
        channel.flushOutbound();
        Object packet = channel.readOutbound();
        if (acknowledgeChunkBatches && packet instanceof ClientboundChunkBatchFinishedPacket) {
            player.connection.handleChunkBatchReceived(new ServerboundChunkBatchReceivedPacket(9.0f));
            chunkBatchAcknowledgements++;
        }
        if (packet instanceof ClientboundChunkBatchFinishedPacket) chunkBatchFinishReceipts++;
        return packet;
    }

    public boolean isRegisteredAndAlive() {
        return playerList.getPlayer(player.getUUID()) == player && player.isAlive() && !player.isRemoved();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        outboundObserver = null;
        // placeNewPlayer can throw after inserting into PlayerList. Membership, not a post-call flag,
        // is the authority for supported normal logout cleanup.
        if (playerListRegistered && playerList.getPlayer(player.getUUID()) == player) {
            playerList.remove(player);
        } else if (player.isAddedToLevel()) {
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        channel.finishAndReleaseAll();
    }
}
