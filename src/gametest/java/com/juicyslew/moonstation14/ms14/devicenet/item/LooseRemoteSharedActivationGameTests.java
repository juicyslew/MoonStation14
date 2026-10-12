package com.juicyslew.moonstation14.ms14.devicenet.item;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.devicenet.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the production shared portable-source activation and queue with a real prepared loose stack. */
@GameTestTags({"device-network-owner", "device-network"})
@PrefixGameTestTemplate(false)
public final class LooseRemoteSharedActivationGameTests {
    private LooseRemoteSharedActivationGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void preparedLooseRemoteUsesSharedInvokeQueueAndResolvedCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DeviceNetworkRuntime runtime = DeviceNetworkRuntime.forLevel(level);
        var sources = new SourceCase[2];
        for (int i = 0; i < sources.length; i++) {
            ItemStack stack = new ItemStack(i == 0 ? ModItems.REMOTE_SIGNALLER.get() : ModItems.REMOTE_SIGNALLER_ADVANCED.get());
            NetworkDeviceItemData policy = ModNetworkDeviceItems.resolve(stack, false).orElseThrow();
            require(policy.signallerPort().isPresent(), "resolved standard/advanced item exposes source port");
            DeviceComposition resolved = policy.deviceComponents().resolve(
                    com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager());
            var network = resolved.network().orElseThrow();
            require(network.transmitFrequency() == 0 && network.receiveFrequency() >= 1,
                    "portable remote retains SS14 zero TX and resolved BasicDevice RX metadata");
            require(Math.abs(resolved.connection().orElseThrow().wirelessRange() - (i == 0 ? 15 : 50)) < .001,
                    "resolved source composition retains standard/advanced wireless range");

            BlockPos position = helper.absolutePos(new BlockPos(3 + i * 4, 2, 3));
            level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), 3);
            LooseItemEntity loose = LooseItemEntity.create(level,
                    new net.minecraft.world.phys.Vec3(position.getX() + .5, position.getY(), position.getZ() + .5), stack);
            require(level.addFreshEntity(loose), "actual loose source entity added");
            require(RemoteSignallerServer.prepareLooseEndpoint(loose), "production preparation publishes canonical stack endpoint");
            RemoteSignallerStackData state = loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REMOTE_SIGNALLER.get());
            require(state != null, "canonical identity/address committed to source stack");
            DeviceEndpoint source = runtime.transport().lookup(state.identity());
            require(source != null && source.available() && source.ownsAddressOutsideWorldSavedData(),
                    "runtime uses actual loose owner adapter, not a synthetic always-available source");

            AtomicInteger nearDelivery = new AtomicInteger();
            AtomicInteger farDelivery = new AtomicInteger();
            DeviceEndpoint near = receiver(level, DeviceIdentity.random(), position.east(2), network.networkId(),
                    network.receiveFrequency(), nearDelivery);
            DeviceEndpoint far = receiver(level, DeviceIdentity.random(), position.east(20), network.networkId(),
                    network.receiveFrequency(), farDelivery);
            require(runtime.loaded(near) && runtime.loaded(far), "native receivers connect to canonical network");
            String pressed = policy.signallerPort().orElseThrow();
            require(runtime.links().link(runtime.graph(), source, pressed, near, "Toggle"),
                    "native graph links resolved Pressed source to nearby receiver");
            // Deliberately retain a persisted link beyond the standard wireless radius to
            // exercise the real transport range gate independently of link-edit admission.
            require(runtime.graph().link(new DeviceLinkGraph.Link(new DeviceLinkGraph.PortRef(source.identity(), pressed),
                    new DeviceLinkGraph.PortRef(far.identity(), "Toggle"))), "far fixture route retained in graph");
            sources[i] = new SourceCase(policy, state, source, nearDelivery, farDelivery);
        }

        long now = level.getGameTime();
        int queueBeforeInvoke = runtime.transport().queuedPackets();
        for (SourceCase source : sources) {
            require(RemoteSignallerServer.activatePrepared(level, source.policy, source.state, source.endpoint)
                    == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                    "shared activation accepts trusted prepared loose source");
        }
        require(runtime.transport().queuedPackets() == queueBeforeInvoke + 4,
                "both source invocations enqueue near and far wireless packets");
        runtime.update();
        require(sources[0].nearDelivery.get() == 1 && sources[0].farDelivery.get() == 0
                        && sources[1].nearDelivery.get() == 1 && sources[1].farDelivery.get() == 1,
                "native deferred drain honors standard 15 and advanced 50 block wireless ranges");
        int queueBeforeImmediateRepeat = runtime.transport().queuedPackets();
        for (SourceCase source : sources) {
            require(RemoteSignallerServer.activatePrepared(level, source.policy, source.state, source.endpoint)
                            == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK
                            && runtime.transport().queuedPackets() == queueBeforeImmediateRepeat,
                    "early repeat is acknowledged without enqueue");
        }
        helper.runAfterDelay(1, () -> {
            require(level.getGameTime() - now >= 1, "fixture reached cooldown tick 1");
            int queueBeforeTickOne = runtime.transport().queuedPackets();
            for (SourceCase source : sources)
                require(RemoteSignallerServer.activatePrepared(level, source.policy, source.state, source.endpoint)
                                == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                        "tick-1 repeat is acknowledged");
            require(runtime.transport().queuedPackets() == queueBeforeTickOne, "tick 1 is a cooldown no-op");
            runtime.update();
            require(sources[0].nearDelivery.get() == 1 && sources[0].farDelivery.get() == 0
                            && sources[1].nearDelivery.get() == 1 && sources[1].farDelivery.get() == 1,
                    "tick-1 no-op emits no extra signal and leaves prior deliveries unchanged");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 18, () -> {
                require(level.getGameTime() - now >= 19, "fixture reached cooldown tick 19");
                int queueBeforeTickNineteen = runtime.transport().queuedPackets();
                for (SourceCase source : sources)
                    require(RemoteSignallerServer.activatePrepared(level, source.policy, source.state, source.endpoint)
                                    == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                            "tick-19 repeat is acknowledged");
                require(runtime.transport().queuedPackets() == queueBeforeTickNineteen,
                        "tick 19 remains a no-op and does not move deadline");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 1, () -> {
                    require(level.getGameTime() - now >= 20, "fixture reached cooldown tick 20");
                    int queueBeforeTickTwenty = runtime.transport().queuedPackets();
                    for (SourceCase source : sources)
                        require(RemoteSignallerServer.activatePrepared(level, source.policy, source.state, source.endpoint)
                                        == com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK,
                                "tick-20 activation passes the shared cooldown");
                    require(runtime.transport().queuedPackets() == queueBeforeTickTwenty + 4,
                            "cooldown expires at 20 ticks");
                    runtime.update();
                    require(sources[0].nearDelivery.get() == 2 && sources[0].farDelivery.get() == 0
                                    && sources[1].nearDelivery.get() == 2 && sources[1].farDelivery.get() == 2,
                            "second native drain preserves range behavior after cooldown expiry");
                    for (SourceCase source : sources) runtime.unloaded(source.state.identity());
                    helper.succeed();
                });
            });
        });
    }

    private static DeviceEndpoint receiver(ServerLevel level, DeviceIdentity identity, BlockPos position,
            int networkId, int receiveFrequency, AtomicInteger delivered) {
        DeviceComposition.NetworkInterface network = new DeviceComposition.NetworkInterface(networkId, false, true,
                "RCV-" + UUID.randomUUID().toString().substring(0, 8), "", 0, receiveFrequency,
                false, false, true, false, true, false);
        DeviceComposition composition = new DeviceComposition(List.of(), List.of(new DevicePort("Toggle", "Toggle", "")),
                Optional.of(network), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(new DeviceComposition.ConnectionConfig(false, true, 50)));
        return new DeviceEndpoint() {
            @Override public DeviceIdentity identity() { return identity; }
            @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return level.dimension(); }
            @Override public BlockPos position() { return position; }
            @Override public DeviceComposition composition() { return composition; }
            @Override public boolean available() { return level.getServer().isSameThread(); }
            @Override public boolean powered() { return true; }
            @Override public void receive(DevicePayload payload, String sourceAddress) {
                if (payload instanceof SignalPayload signal && signal.portId().equals("Toggle")
                        && signal.signal() == DeviceSignal.MOMENTARY) delivered.incrementAndGet();
            }
        };
    }

    private record SourceCase(NetworkDeviceItemData policy, RemoteSignallerStackData state,
                              DeviceEndpoint endpoint, AtomicInteger nearDelivery, AtomicInteger farDelivery) { }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
