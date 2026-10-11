package com.juicyslew.moonstation14.gametest.support;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;

/** Socket-free, normal PlayerList login fixture. This proves registration, not authenticated admission. */
public final class RegisteredServerPlayerFixture implements AutoCloseable {
    private final PlayerList playerList;
    private final ServerPlayer player;
    private final EmbeddedChannel channel;
    private boolean closed;
    private final Connection connection;

    private RegisteredServerPlayerFixture(PlayerList playerList, ServerPlayer player, Connection connection, EmbeddedChannel channel) {
        this.playerList = playerList;
        this.player = player;
        this.connection = connection;
        this.channel = channel;
    }

    public static RegisteredServerPlayerFixture register(ServerLevel level, String name) {
        MinecraftServer server = level.getServer();
        UUID id = UUID.randomUUID();
        // Player profile names are limited to 16 characters; the UUID suffix also prevents world-local
        // profile-store collisions between GameTest invocations.
        String suffix = id.toString().replace("-", "").substring(0, 8);
        String profileName = (name.length() > 7 ? name.substring(0, 7) : name) + suffix;
        GameProfile profile = new GameProfile(id, profileName);
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
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // Connection is installed as the EmbeddedChannel handler: channelActive binds the real
        // Connection to this channel, and PlayerList installs its normal inbound play protocol.
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        RegisteredServerPlayerFixture fixture = new RegisteredServerPlayerFixture(server.getPlayerList(), player, connection, channel);
        try {
            // NeoForge's documented test hook installs the complete negotiated payload setup for a
            // synthetic NeoForge peer. This preserves NetworkRegistry send checks; it is not proof of
            // handshake/codec interoperability with an actual client.
            NetworkRegistry.configureMockConnection(connection);
            var cookie = new CommonListenerCookie(profile, 0,
                    net.minecraft.server.level.ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            fixture.playerList.placeNewPlayer(connection, player, cookie);
            return fixture;
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    public ServerPlayer player() { return player; }
    public Connection connection() { return connection; }

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
        if (playerList.getPlayer(player.getUUID()) == player) {
            playerList.remove(player);
        }
        channel.finishAndReleaseAll();
    }
}
