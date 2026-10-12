package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems;
import com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerServer;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Owner-path proof for body-eye admission and portable signaller composition. */
@PrefixGameTestTemplate(false)
@com.juicyslew.moonstation14.gametest.support.GameTestTags({"device-network-owner", "device-network", "hands", "interaction"})
public final class LooseConfiguratorTargetGameTests {
    private LooseConfiguratorTargetGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 20)
    public static void nominatedLooseSignallerUsesResolvedCompositionAndBoundedRay(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var body = EntityType.ZOMBIE.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        // Give the mob and the loose item real floor support, then target the item's actual
        // bounding-box center from the mob's actual eyes rather than assuming a horizontal ray.
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.east().below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.east(2).below(), Blocks.STONE.defaultBlockState(), 3);
        body.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        require(level.addFreshEntity(body), "body added");
        warmAimChunks(level, body);

        LooseItemEntity wallTarget = null;
        for (var item : new net.minecraft.world.item.Item[]{ModItems.REMOTE_SIGNALLER.get(), ModItems.REMOTE_SIGNALLER_ADVANCED.get()}) {
            if (wallTarget != null) wallTarget.discard();
            ItemStack stack = new ItemStack(item);
            require(ModNetworkDeviceItems.isSignaller(stack), "item resolves through server network-device prototype");
            LooseItemEntity loose = LooseItemEntity.create(level, body.position().add(2, 0, 0), stack);
            require(level.addFreshEntity(loose), "loose entity added");
            body.lookAt(EntityAnchorArgument.Anchor.EYES, loose.getBoundingBox().getCenter());
            warmAimChunks(level, body);
            var target = BodyItemTargetProbe.probeSelected(level, body, loose.getUUID(), BodyItemTargetProbe.MAX_REACH);
            require(target != null, "exact nominated signaller is in bounded visible candidate set; eye="
                    + body.getEyePosition() + ", look=" + body.getLookAngle() + ", box=" + loose.getBoundingBox());
            require(BodyItemTargetProbe.probeSelected(level, body, java.util.UUID.randomUUID(), BodyItemTargetProbe.MAX_REACH) == null,
                    "non-candidate nomination rejected");
            require(RemoteSignallerServer.prepareLooseEndpoint(loose), "existing loose endpoint preparation succeeds");
            var state = loose.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.REMOTE_SIGNALLER.get());
            require(state != null && !state.identity().value().equals(new java.util.UUID(0, 0)), "identity metadata persisted on item");
            var portable = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level)
                    .transport().lookupAt(loose.blockPosition()).stream()
                    .filter(endpoint -> endpoint.identity().equals(state.identity())).findFirst().orElse(null);
            require(portable != null && portable.available(), "prepared portable endpoint remains registered");
            var composition = portable.composition();
            String declaredPort = ModNetworkDeviceItems.resolve(loose.getItem(), false).orElseThrow()
                    .signallerPort().orElseThrow();
            require("Pressed".equals(declaredPort)
                            && composition.sources().stream().anyMatch(source -> declaredPort.equals(source.id())),
                    "resolved signaller source port id matches declared Pressed route; declared=" + declaredPort
                            + ", resolved=" + composition.sources());
            var address = composition.network().orElseThrow().address();
            require(!address.isBlank(), "network address is resolved from prototype/state");
            loose.setPos(loose.position().add(0, 0, .1));
            require(RemoteSignallerServer.prepareLooseEndpoint(loose), "move refreshes the existing portable registration");
            var moved = com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime.forLevel(level)
                    .transport().lookupAt(loose.blockPosition()).stream()
                    .filter(endpoint -> endpoint.identity().equals(state.identity())).findFirst().orElse(null);
            require(moved != null && address.equals(moved.composition().network().orElseThrow().address()),
                    "portable address remains stable across movement");
            wallTarget = loose;
        }
        // A blocker must prevent nomination; no ray fallback behind it is permitted.
        var candidate = wallTarget;
        require(candidate != null, "fixture retains dropped candidate");
        body.lookAt(EntityAnchorArgument.Anchor.EYES, candidate.getBoundingBox().getCenter());
        warmAimChunks(level, body);
        double blockerDistance = body.getEyePosition().distanceTo(candidate.getBoundingBox().getCenter()) * .5;
        BlockPos wall = BlockPos.containing(body.getEyePosition().add(body.getLookAngle().scale(blockerDistance)));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        require(BodyItemTargetProbe.probeSelected(level, body, candidate.getUUID(), BodyItemTargetProbe.MAX_REACH) == null,
                "wall occlusion rejects nominated loose target");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** Load exactly the production probe's bounded ray chunk envelope as fixture setup. */
    private static void warmAimChunks(net.minecraft.server.level.ServerLevel level,
                                      net.minecraft.world.entity.LivingEntity body) {
        var from = body.getEyePosition();
        var to = from.add(body.getLookAngle().scale(BodyItemTargetProbe.MAX_REACH));
        int minX = (net.minecraft.util.Mth.floor(Math.min(from.x, to.x)) >> 4) - 1;
        int maxX = (net.minecraft.util.Mth.floor(Math.max(from.x, to.x)) >> 4) + 1;
        int minZ = (net.minecraft.util.Mth.floor(Math.min(from.z, to.z)) >> 4) - 1;
        int maxZ = (net.minecraft.util.Mth.floor(Math.max(from.z, to.z)) >> 4) + 1;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            level.getChunkSource().getChunk(x, z,
                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
    }
}
