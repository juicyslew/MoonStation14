package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.juicyslew.moonstation14.entities.ModEntities;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.ms14.radiation.RadiationService;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated active-body classification contract used by the adapted player lethal core. */
@GameTestTags({"singularity-gravity", "singularity-horizon"})
@PrefixGameTestTemplate(false)
public final class SingularityPlayerCoreClassificationGameTests {
    private SingularityPlayerCoreClassificationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void ordinaryUnregisteredServerPlayerIsACharacterBodyNotACarrier(GameTestHelper helper) {
        // This is a real native ServerPlayer with a live packet connection and level membership,
        // but no PlayerList registration, login event, or authenticated lifecycle admission.
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.createUnenrolled(helper.getLevel(), "ordinarycore");
        ServerPlayer player = fixture.player();
        var level = helper.getLevel();
        SingularityEntity[] source = {null};
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            private void cleanup() {
                if (source[0] != null) {
                    RadiationService.INSTANCE.remove(level, source[0].identity());
                    source[0].discard();
                }
                fixture.close();
            }
        });
        // This ordinary ServerPlayer was added to the world without PlayerList login, so it has
        // not been lifecycle-enrolled/parked as a spectator by PlayerLoggedInEvent.
        boolean playerListMember = level.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
        require(player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL
                        && !playerListMember && !ActiveCharacterPolicy.isCarrier(player),
                "ordinary unenrolled actor is level-admitted survival, absent from PlayerList, not a carrier"
                        + " [gameMode=" + player.gameMode.getGameModeForPlayer() + ", spectator=" + player.isSpectator()
                        + ", playerListMember=" + playerListMember + ", carrier=" + ActiveCharacterPolicy.isCarrier(player) + "]");
        require(!ActiveCharacterPolicy.isCarrier(player), "ordinary ServerPlayer is not an experimental carrier");
        require(ActiveCharacterPolicy.isPlayerCharacterBody(player, level),
                "ordinary ServerPlayer receives player-character body classification");
        source[0] = ModEntities.SINGULARITY.get().create(level);
        require(source[0] != null, "stage-4 source created");
        source[0].initializeForGravityFixture(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity"), 1100);
        var center = helper.absolutePos(new net.minecraft.core.BlockPos(3, 40, 3));
        source[0].setPos(center.getX() + .5, center.getY() + .5, center.getZ() + .5);
        require(level.addFreshEntity(source[0]), "source admitted for native contact");
        double previousCoreRadius = source[0].gravityProfile().eventHorizonRadiusAtLevel(4) * .35;
        player.setPos(source[0].getX() + previousCoreRadius + .25, source[0].getY(), source[0].getZ());
        require(SingularityHorizonGeometry.touches(source[0].position(), previousCoreRadius, player.getBoundingBox())
                        && !SingularityHorizonGeometry.touches(source[0].position(), source[0].gravityProfile().playerLethalRadiusAtLevel(4), player.getBoundingBox()),
                "ordinary player's actual bounds cross the former uncapped core but not the capped core");
        source[0].tick();
        require(player.isAlive(), "ordinary ServerPlayer survives L4 old 1.225-radius boundary");
        player.setPos(source[0].getX() + .5, source[0].getY(), source[0].getZ());
        require(SingularityHorizonGeometry.touches(source[0].position(), source[0].gravityProfile().playerLethalRadiusAtLevel(4), player.getBoundingBox()),
                "ordinary player's actual bounds enter the capped L4 core");
        boolean indexed = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                source[0].getBoundingBox().inflate(source[0].gravityProfile().eventHorizonRadiusAtLevel(4)),
                candidate -> candidate == player).contains(player);
        require(player.isAlive() && !player.isSpectator() && !player.noPhysics && player.isAddedToLevel()
                        && !ActiveCharacterPolicy.isCarrier(player) && indexed,
                "ordinary native player is eligible for horizon query before contact [alive=" + player.isAlive()
                        + ", spectator=" + player.isSpectator() + ", noPhysics=" + player.noPhysics
                        + ", added=" + player.isAddedToLevel() + ", carrier=" + ActiveCharacterPolicy.isCarrier(player)
                        + ", indexed=" + indexed + ", pos=" + player.position() + ", bounds=" + player.getBoundingBox()
                        + ", source=" + source[0].position() + ", core="
                        + source[0].gravityProfile().playerLethalRadiusAtLevel(4) + "]");
        source[0].tick();
        require(player.isDeadOrDying(), "ordinary ServerPlayer dies only after actual L4 capped-core contact [health="
                + player.getHealth() + ", alive=" + player.isAlive() + ", dead=" + player.isDeadOrDying()
                + ", pos=" + player.position() + ", bounds=" + player.getBoundingBox()
                + ", source=" + source[0].position() + ", sourceLevel=" + source[0].singularityLevel()
                + ", gameTime=" + level.getGameTime() + "]");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void onlyAuthenticatedActiveCharacterBodyUsesScaledPlayerCore(GameTestHelper helper) {
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "corebody");
        var level = helper.getLevel();
        var singularities = new java.util.ArrayList<SingularityEntity>();
        var owned = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            private void cleanup() {
                fixture.close();
                owned.forEach(net.minecraft.world.entity.Entity::discard);
                singularities.forEach(source -> { RadiationService.INSTANCE.remove(level, source.identity()); source.discard(); });
            }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var player = fixture.player();
                var body = active.body();
                owned.add(body);
                require(ActiveCharacterPolicy.isCarrier(player), "registered experimental carrier is excluded from player core");
                require(!ActiveCharacterPolicy.isPlayerCharacterBody(player, helper.getLevel()),
                        "carrier itself is not a player-controlled character body");
                require(ActiveCharacterPolicy.isPlayerCharacterBody(body, helper.getLevel()),
                        "exact authenticated active CHARACTER body is a player-core target");

                var unrelated = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
                require(unrelated != null, "unrelated NPC created");
                require(!ActiveCharacterPolicy.isPlayerCharacterBody(unrelated, helper.getLevel()),
                        "unrelated NPC does not gain player-core classification");
                owned.add(unrelated);

                var source = ModEntities.SINGULARITY.get().create(level);
                require(source != null, "registered singularity created");
                singularities.add(source);
                // Keep the live contact fixture above the template so the source's first
                // scheduled matter wave cannot credit nearby terrain alongside the NPC.
                var sourcePos = helper.absolutePos(new BlockPos(3, 40, 3));
                source.setPos(sourcePos.getX() + .5, sourcePos.getY() + .5, sourcePos.getZ() + .5);
                source.feed(1100); // resolved level 4; genuine source tick/contact path below
                require(level.addFreshEntity(source), "singularity admitted");
                var resolved = com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.catalog(level)
                        .get(source.profileId());
                require(resolved != null && resolved.eventHorizon().playerLethalRadiusScale() == .35
                                && resolved.eventHorizon().playerLethalRadiusMax() == .35,
                        "live source uses resolved profile's authored scale and block-radius cap");
                double oldRadius = resolved.eventHorizonRadiusAtLevel(source.singularityLevel());
                double coreRadius = resolved.playerLethalRadiusAtLevel(source.singularityLevel());
                require(source.singularityLevel() == 4 && oldRadius == 3.5 && coreRadius == .35,
                        "live stage-4 source keeps general horizon and capped tiny player core");

                // With the former uncapped 0.35 * L4 horizon, this body's closest face
                // (about 1.175 blocks) touched the 1.225-block player core; it must now survive.
                double boundaryX = source.position().x + oldRadius * .35 + .25;
                body.setPos(boundaryX, source.position().y, source.position().z);
                unrelated.setPos(boundaryX, source.position().y, source.position().z);
                player.setPos(source.position().x + .5, source.position().y, source.position().z);
                require(level.addFreshEntity(unrelated), "unrelated NPC admitted");
                require(SingularityHorizonGeometry.touches(source.position(), oldRadius, body.getBoundingBox())
                                && !SingularityHorizonGeometry.touches(source.position(), coreRadius, body.getBoundingBox()),
                        "active body's real bounds lie inside old horizon and outside player core");
                require(SingularityHorizonGeometry.touches(source.position(), oldRadius * .35, body.getBoundingBox()),
                        "active body's actual bounds intersect the former uncapped core boundary");
                require(SingularityHorizonGeometry.touches(source.position(), oldRadius, unrelated.getBoundingBox()),
                        "NPC bounds lie inside unchanged general horizon");
                double beforeNpc = source.energy();
                source.tick();
                require(body.isAlive(), "active character body survives old-horizon-only contact");
                require(unrelated.isDeadOrDying() || unrelated.isRemoved(), "NPC still dies at the unchanged old horizon");
                double afterNpc = resolved.energyAfterSeconds(beforeNpc, source.singularityLevel(), .05)
                        + resolved.eventHorizon().entityEnergy();
                require(Math.abs(source.energy() - afterNpc) < .02,
                        "NPC death credits once without terrain/other matter [before=" + beforeNpc
                                + ", actual=" + source.energy() + ", expected=" + afterNpc
                                + ", level=" + source.singularityLevel() + ", gameTime=" + level.getGameTime()
                                + ", npcDead=" + (unrelated.isDeadOrDying() || unrelated.isRemoved())
                                + ", bodyAlive=" + body.isAlive() + "]");

                body.setPos(source.position().x + .5, source.position().y, source.position().z);
                require(SingularityHorizonGeometry.touches(source.position(), coreRadius, body.getBoundingBox()),
                        "active body's real bounds now intersect resolved player core");
                double beforeCore = source.energy();
                source.tick();
                require(body.isDeadOrDying() || body.isRemoved(), "actual source contact admits native active-body death inside core");
                require(body instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity
                                && ((com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity) body).hasConfirmedDeath(),
                        "active body's existing death admission confirms the native death callback");
                double afterCore = resolved.energyAfterSeconds(beforeCore, source.singularityLevel(), .05)
                        + resolved.eventHorizon().entityEnergy();
                require(Math.abs(source.energy() - afterCore) < .02, "confirmed active-body death credits once");
                double beforeRepeat = source.energy();
                source.tick();
                require(Math.abs(source.energy() - resolved.energyAfterSeconds(beforeRepeat, source.singularityLevel(), .05)) < .02,
                        "retained dead active-character corpse cannot feed twice");
                require(player.isAlive(), "carrier positioned inside the core remains excluded");
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 80)
    public static void levelThreeSingularityNaturallyLiftsGroundedAuthenticatedCharacter(GameTestHelper helper) {
        var level = helper.getLevel();
        var fixture = RegisteredServerPlayerFixture.register(level, "corelift");
        var floor = new java.util.ArrayList<BlockPos>();
        var sources = new java.util.ArrayList<SingularityEntity>();
        var owned = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        final boolean[] movementComplete = {false};
        final long[] movementSequence = {0};
        final RegisteredPlayerReadyDriver.Active[] activePlayer = {null};
        // Register this recurring task before the scheduled setup callback. GameTest's tick
        // scheduler can revisit a task added while it is executing, replaying one-shot auth setup.
        helper.onEachTick(() -> {
            var active = activePlayer[0];
            if (active != null && !movementComplete[0]) {
                RegisteredPlayerReadyDriver.submitNeutralMovement(fixture, active, ++movementSequence[0]);
                // The embedded fixture is not in ServerConnectionListener's live connection set.
                // Advance only its own normal listener tick so vanilla/NeoForge player tick hooks run.
                fixture.tickConnection();
            }
        });
        Runnable cleanup = () -> {
            movementComplete[0] = true;
            activePlayer[0] = null;
            fixture.close();
            owned.forEach(net.minecraft.world.entity.Entity::discard);
            sources.forEach(source -> { RadiationService.INSTANCE.remove(level, source.identity()); source.discard(); });
            floor.forEach(pos -> level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3));
        };
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            private void cleanup() { cleanup.run(); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                activePlayer[0] = active;
                var body = (com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity) active.body();
                owned.add(body);
                // The registered fixture has no real client loop; the pre-registered per-tick
                // task now exercises the canonical authenticated neutral intent path.
                var base = helper.absolutePos(new BlockPos(3, 0, 3));
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    BlockPos p = base.offset(x, 0, z);
                    floor.add(p);
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
                body.teleportTo(base.getX() + .5, base.getY() + 1, base.getZ() + .5);
                BoundedGameTestWait.await(helper, 20,
                        () -> "authenticated body did not become physically grounded on placed stone within 20 ticks"
                                + " [class=" + body.getClass().getName() + ", pos=" + body.position()
                                + ", bounds=" + body.getBoundingBox() + ", velocity=" + body.getDeltaMovement()
                                + ", floor=" + base + ", floorState=" + level.getBlockState(base)
                                + ", added=" + body.isAddedToLevel() + ", noPhysics=" + body.noPhysics
                                + ", passenger=" + body.isPassenger() + ", alive=" + body.isAlive() + "]",
                        body::onGround, () -> {
                    try {
                        double floorY = body.getY();
                        var source = ModEntities.SINGULARITY.get().create(level);
                        require(source != null, "singularity source created");
                        sources.add(source);
                        source.setPos(body.getX(), floorY + 5, body.getZ());
                        source.feed(500); // authored level 3
                        require(level.addFreshEntity(source), "source admitted to ordinary world ticking");
                        require(source.singularityLevel() == 3, "source is level 3");
                        BoundedGameTestWait.await(helper, 40,
                                () -> "level 3 source produced no live-body lift within 40 ticks"
                                        + " [pos=" + body.position() + ", velocity=" + body.getDeltaMovement()
                                        + ", onGround=" + body.onGround() + ", alive=" + body.isAlive()
                                        + ", floorY=" + floorY + ", sourcePos=" + source.position()
                                        + ", sourceLevel=" + source.singularityLevel() + "]",
                                 () -> body.isAlive() && body.getY() > floorY + .02 && !body.onGround(), () -> {
                            movementComplete[0] = true;
                            helper.succeed();
                        });
                    } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
                });
            } catch (RuntimeException | Error failure) { cleanup.run(); throw failure; }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
