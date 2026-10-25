package com.juicyslew.moonstation14.gametest.support;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.GameProtocols;
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

/** Socket-free native ServerPlayer fixtures for either ordinary PlayerList login or unenrolled world admission. */
public final class RegisteredServerPlayerFixture implements AutoCloseable {
    private final PlayerList playerList;
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private boolean closed;
    private final Connection connection;
    private final ServerLevel level;
    private final boolean playerListRegistered;

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
            var listener = new ServerGamePacketListenerImpl(server, connection, player, cookie);
            connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                    RegistryFriendlyByteBuf.decorator(server.registryAccess(), listener.getConnectionType())), listener);
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

    /** Advances this socket-free connection's ordinary serverbound play listener once. */
    public void tickConnection() {
        if (closed) throw new IllegalStateException("registered player fixture is closed");
        connection.tick();
    }

    /** Outbound login/lifecycle packets remain available for a fixture's client-side Begin/Ready driver. */
    public Object pollOutbound() {
        // PacketDistributor/Connection may defer writes on the embedded event loop. Advance only
        // this fixture channel so ordinary outbound payload assertions observe completed writes.
        channel.runPendingTasks();
        channel.flushOutbound();
        return channel.readOutbound();
    }

    public boolean isRegisteredAndAlive() {
        return playerList.getPlayer(player.getUUID()) == player && player.isAlive() && !player.isRemoved();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
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
