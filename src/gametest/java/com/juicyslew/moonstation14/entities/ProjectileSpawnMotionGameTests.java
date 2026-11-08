package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"reactor-presentation", "pa-client-sync"})
@PrefixGameTestTemplate(false)
public final class ProjectileSpawnMotionGameTests {
    private ProjectileSpawnMotionGameTests() { }
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=30)
    public static void emitterAddPacketSeedsMotionWithoutMovingEntity(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        var source = ModEntities.EMITTER_BOLT.get().create(level);
        var standin = ModEntities.EMITTER_BOLT.get().create(level);
        check(source != null && standin != null, "registered emitter entities construct");
        assertSpawnMotion(level, source, standin, new Vec3(1.25, -2, .75));
        helper.succeed();
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=30)
    public static void particleAddPacketSeedsMotionWithoutMovingEntity(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        var source = ModEntities.PA_PARTICLE.get().create(level);
        var standin = ModEntities.PA_PARTICLE.get().create(level);
        check(source != null && standin != null, "registered particle entities construct");
        assertSpawnMotion(level, source, standin, new Vec3(-1.5, .5, 2.25));
        helper.succeed();
    }

    private static void assertSpawnMotion(ServerLevel level, net.minecraft.world.entity.Entity source,
                                          net.minecraft.world.entity.Entity standin, Vec3 motion) {
        BlockPos position = new BlockPos(4, 5, 6);
        source.setPos(position.getX() + .25, position.getY() + .5, position.getZ() + .75);
        source.setDeltaMovement(motion);
        ServerEntity tracker = new ServerEntity(level, source, 1, true, packet -> { });
        ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(source, tracker);
        standin.setPos(-10, -10, -10);
        standin.recreateFromPacket(packet);
        Vec3 packetPosition = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        Vec3 packetMotion = new Vec3(packet.getXa(), packet.getYa(), packet.getZa());
        check(standin.position().equals(packetPosition), "recreate uses packet position without advancing");
        check(standin.getDeltaMovement().equals(packetMotion) && !packetMotion.equals(Vec3.ZERO), "spawn packet seeds nonzero authoritative motion");
        standin.lerpMotion(packet.getXa(), packet.getYa(), packet.getZa());
        check(standin.position().equals(packetPosition) && standin.getDeltaMovement().equals(packetMotion), "reapplying identical motion does not move or accumulate");
    }
}
