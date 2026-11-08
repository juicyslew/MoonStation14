package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Authenticated registered-player fixture exercising the production offer/ready/commit exchange. */
@PrefixGameTestTemplate(false)
@GameTestTags({"lifecycle", "mouse-ghost-roles", "development-possession"})
public final class DevelopmentPossessionGameTests {
    private DevelopmentPossessionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void operatorDevelopmentOfferPreservesOriginalBatThroughCommit(GameTestHelper helper) {
        exercise(helper, "moonstation14:bat");
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80)
    public static void operatorDevelopmentOfferPreservesOriginalKoboldThroughCommit(GameTestHelper helper) {
        exercise(helper, "moonstation14:kobold");
    }

    private static void exercise(GameTestHelper helper, String typeId) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "DevPossess");
        level.getServer().getPlayerList().getOps().add(new ServerOpListEntry(fixture.player().getGameProfile(), 4, true));
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .get(ResourceLocation.parse(typeId));
        if (type == null || type == net.minecraft.world.entity.EntityType.PIG)
            throw new GameTestAssertException("development fixture target type missing: " + typeId);
        var target = type.create(level);
        if (!(target instanceof Mob mob)) throw new GameTestAssertException("mapped fixture target is not a Mob");
        mob.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3)).getCenter());
        if (!level.addFreshEntity(mob)) throw new GameTestAssertException("mapped target failed to load");
        var character = com.juicyslew.moonstation14.ms14.character.ModCharacters.characterForHost(level,
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())).orElseThrow();
        com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(mob, level, character);
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(active.body(),
                        java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 300f), 1f, true);
                helper.runAfterDelay(3, () -> {
                    try {
                        var player = fixture.player();
                        var corpse = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                        if (corpse == null || !com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem
                                .requestGhost(player, corpse.epoch())) throw new GameTestAssertException("ghost entry failed");
                        var ghost = com.juicyslew.moonstation14.ms14.medical_observer.ObserverSystem.query(player);
                        if (ghost == null || !RegisteredPlayerReadyDriver.readyGhost(fixture, ghost.authorityGeneration()))
                            throw new GameTestAssertException("existing GHOST Begin/Ready failed");
                        try {
                            player.getServer().getCommands().getDispatcher().execute(
                                    player.getServer().getCommands().getDispatcher().parse(
                                             "ms14dev possess " + mob.getUUID(),
                                            player.createCommandSourceStack()));
                        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                            throw new GameTestAssertException("development possession command failed: " + failure);
                        }
                        com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer offer = null;
                        Object packet;
                        while ((packet = fixture.pollOutbound()) != null)
                            if (packet instanceof ClientboundCustomPayloadPacket p
                                    && p.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Offer found)
                                offer = found;
                        if (offer == null) throw new GameTestAssertException("development offer packet missing");
                        if (offer.targetEntityId() != mob.getId())
                            throw new GameTestAssertException("development offer targeted entity id=" + offer.targetEntityId()
                                    + " instead of fixture id=" + mob.getId() + " uuid=" + mob.getUUID());
                         RegisteredPlayerReadyDriver.acceptRoleOffer(fixture, offer);
                         var controlled = LifecycleCharacterSessionControl.activeCharacterBody(player).orElse(null);
                         if (controlled == null || controlled.body() != mob || !mob.getUUID().equals(controlled.body().getUUID()))
                             throw new GameTestAssertException("commit replaced target rather than adopting original UUID");
                         var role = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob)
                                 .flatMap(data -> data.component(com.juicyslew.moonstation14.ms14.character.components.GhostRoleComponent.class));
                          if (typeId.equals("moonstation14:bat") && role.isPresent())
                              throw new GameTestAssertException("development bat unexpectedly has ordinary GhostRoleComponent");
                          if (typeId.equals("moonstation14:kobold") && role.isEmpty())
                              throw new GameTestAssertException("mapped kobold lost its ordinary GhostRoleComponent");
                         helper.runAfterDelay(1, () -> exerciseDevelopmentMovement(helper, fixture, mob, controlled.epoch()));
                    } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
                });
            } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
        });
    }

    private static void exerciseDevelopmentMovement(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
                                                     Mob mob, long epoch) {
        try {
            double startX = mob.getX();
            var first = new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent(
                    epoch, 1, (short) 1000, (short) 0, (byte) 0, 0, 0f, 0f);
            RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, first);
            helper.runAfterDelay(1, () -> {
              try {
                var second = new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent(
                        epoch, 2, (short) 1000, (short) 0, (byte) 0, 0, 0f, 0f);
                RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, second);
                if (mob.getX() <= startX + 0.01)
                    throw new GameTestAssertException(movementFailure("motion did not advance", startX, mob, null));
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot snapshot = null;
            Object packet;
            while ((packet = fixture.pollOutbound()) != null)
                if (packet instanceof ClientboundCustomPayloadPacket p
                        && p.payload() instanceof com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot found
                        && found.epoch() == epoch && found.harnessEntityId() == mob.getId()) snapshot = found;
            if (snapshot == null || snapshot.acknowledgedAppliedSequence() != 2 || snapshot.x() <= startX + 0.01)
                throw new GameTestAssertException(movementFailure("snapshot mismatch", startX, mob, snapshot));
                double releasedX = mob.getX();
                helper.runAfterDelay(1, () -> {
                try {
                    var release = new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent(
                            epoch, 3, (short) 0, (short) 0, (byte) 0, 0, 0f, 0f);
                    RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture, release);
                    if (mob.getX() > releasedX + 0.25)
                        throw new GameTestAssertException("released development movement input continued driving the bat");
                    fixture.close(); mob.discard(); helper.succeed();
                } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
                });
              } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
            });
        } catch (RuntimeException | Error failure) { fixture.close(); mob.discard(); throw failure; }
    }

    private static String movementFailure(String reason, double startX, Mob mob,
            com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot snapshot) {
        long ack = snapshot == null ? -1L : snapshot.acknowledgedAppliedSequence();
        String message = "motion failure: " + reason + " intentYaw=0 currentYaw=" + mob.getYRot()
                + " ack=" + ack + " body=(" + mob.getX() + "," + mob.getY() + "," + mob.getZ()
                + ") startX=" + startX + " deltaX=" + (mob.getX() - startX);
        String context = " onGround=" + mob.onGround() + " horizontalCollision=" + mob.horizontalCollision
                + " verticalCollision=" + mob.verticalCollision;
        return message.length() + context.length() <= 512 ? message + context : message;
    }
}
