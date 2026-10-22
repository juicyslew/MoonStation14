package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.AmbientVocalizationComponent;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestTags({"mouse-ai", "audio", "npc"})
public final class NpcAmbientVocalizationGameTests {
    private NpcAmbientVocalizationGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 450, batch = "npc_ambient_ordinary")
    public static void ordinaryResolvedMouseBroadcastsOneSpatialSqueakAtRandomizedCadence(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        clearPriorTestMice(helper, level);
        var spawn = helper.absolutePos(new net.minecraft.core.BlockPos(8, 1, 8));
        var tickets = FixtureChunkTickets.around(helper, spawn, spawn, 1);
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 20, () -> {
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "normal mouse factory creates entity");
        var sounds = new ArrayList<PlayLevelSoundEvent>();
        long[] firstSoundTick = {-1};
        long[] spawnTick = {level.getGameTime()};
        int[] spawnEntityTick = {-1};
        boolean[] cadenceValidated = {false};
        AmbientVocalizationComponent[] policy = {null};
        Consumer<PlayLevelSoundEvent.AtEntity>[] listenerRef = new Consumer[1];
        Consumer<PlayLevelSoundEvent.AtEntity> listener = event -> {
            if (event.getLevel() == level && event.getEntity() == mouse && event.getSound() != null
                    && event.getSound().value() == ModSounds.MOUSE_SQUEAK.get()) {
                sounds.add(event);
                if (firstSoundTick[0] < 0) {
                    firstSoundTick[0] = level.getGameTime();
                    require(event.getSource() == SoundSource.NEUTRAL, "ambient squeak uses neutral source");
                    require(event.getOriginalVolume() == policy[0].volume(), "broadcast uses resolved profile volume");
                    require(event.getOriginalPitch() == 1f, "ambient squeak uses neutral pitch");
                    helper.runAfterDelay(1, () -> {
                        try {
                            require(sounds.size() == 1, "one ambient squeak was broadcast; no duplicate on the next tick");
                            require(firstSoundTick[0] - spawnTick[0] >= policy[0].minimumIntervalTicks()
                                            && firstSoundTick[0] - spawnTick[0] <= policy[0].maximumIntervalTicks() + 2,
                                    "first broadcast falls within resolved randomized cadence");
                            require(mouse.tickCount - spawnEntityTick[0] >= policy[0].minimumIntervalTicks(),
                                    "mouse received native entity ticks through minimum cadence");
                            cadenceValidated[0] = true;
                            helper.succeed();
                        } finally {
                            NeoForge.EVENT_BUS.unregister(listenerRef[0]);
                        }
                    });
                }
            }
        };
        listenerRef[0] = listener;
        level.setBlock(spawn.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        mouse.setPos(spawn.getCenter());
        // Keep native wandering from carrying the fixture out of its entity-ticking chunk.
        // This affects vanilla AI only; the NPC system and its ordinary tick path remain active.
        mouse.setNoAi(true);
        mouse.setNoGravity(true);
        try {
            require(level.addFreshEntity(mouse), "mouse joins through ordinary entity lifecycle");
            var resolved = CharacterIdentitySystem.resolveForActor(mouse).orElseThrow();
            policy[0] = resolved.component(AmbientVocalizationComponent.class).orElseThrow();
            require(resolved.component(SpeechComponent.class).map(SpeechComponent::speechSound)
                    .filter("mouse_squeak"::equals).isPresent(), "mouse resolves existing speech cue");
            spawnTick[0] = level.getGameTime();
            spawnEntityTick[0] = mouse.tickCount;
            NeoForge.EVENT_BUS.addListener(listener);
            // Keep a margin over the resolved inclusive maximum; diagnostics below distinguish
            // producer scheduling from a missed sound-listener observation.
            helper.runAfterDelay(410, () -> {
                try {
                    require(cadenceValidated[0], "resolved autonomous mouse emitted and passed cadence/duplicate assertions before limit; "
                            + "alive=" + MobStateSystem.isAlive(mouse) + " npc="
                            + ((com.juicyslew.moonstation14.ms14.player_body_control.character.NpcMovementDriven) (Object) mouse)
                            .moonstation14$isNpcMovementDriven() + " now=" + level.getGameTime()
                            + " spawned=" + spawnTick[0] + " max=" + policy[0].maximumIntervalTicks()
                            + " due=" + NpcAmbientVocalizationSystem.scheduledTickForTest(mouse.getUUID())
                            + " pos=" + mouse.blockPosition());
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listenerRef[0]);
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            if (listenerRef[0] != null) NeoForge.EVENT_BUS.unregister(listenerRef[0]);
            throw failure;
        }
        }, spawn);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 35, batch = "npc_ambient_vanilla")
    public static void vanillaMobWithoutResolvedAmbientProfileRemainsSilent(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        clearPriorTestMice(helper, level);
        var sounds = new ArrayList<PlayLevelSoundEvent>();
        Consumer<PlayLevelSoundEvent> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null
                    && event.getSound().value() == ModSounds.MOUSE_SQUEAK.get()) sounds.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER,
                    new net.minecraft.core.BlockPos(8, 1, 8));
            helper.runAfterDelay(30, () -> {
                try {
                    require(sounds.isEmpty(), "uncomposed vanilla mob does not emit ambient mouse audio");
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            throw failure;
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 190, batch = "npc_ambient_owned")
    public static void movementOwnedMouseDoesNotEmitAutonomousSqueaks(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        clearPriorTestMice(helper, level);
        var sounds = new ArrayList<PlayLevelSoundEvent>();
        Consumer<PlayLevelSoundEvent> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null
                    && event.getSound().value() == ModSounds.MOUSE_SQUEAK.get()) sounds.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
            require(mouse != null, "normal mouse factory creates entity");
            ((com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob) (Object) mouse)
                    .moonstation14$setMovementOwned(true);
            var spawn = helper.absolutePos(new net.minecraft.core.BlockPos(8, 1, 8));
            level.setBlock(spawn.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            mouse.setPos(spawn.getCenter());
            require(level.addFreshEntity(mouse), "movement-owned mouse joins normally");
            helper.runAfterDelay(170, () -> {
                try {
                    require(sounds.isEmpty(), "movement-owned mouse remains silent past minimum interval");
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            throw failure;
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 430, batch = "npc_ambient_incapacitated")
    public static void criticalAndDeadMouseRemainSilent(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        clearPriorTestMice(helper, level);
        var sounds = new ArrayList<PlayLevelSoundEvent>();
        Consumer<PlayLevelSoundEvent> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null
                    && event.getSound().value() == ModSounds.MOUSE_SQUEAK.get()) sounds.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var critical = spawnMouse(helper, level, new net.minecraft.core.BlockPos(6, 1, 8));
            var dead = spawnMouse(helper, level, new net.minecraft.core.BlockPos(10, 1, 8));
            require(com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(critical,
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 10f), 1f, true)
                    == com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED
                    && MobStateSystem.isCritical(critical), "typed damage moves normal mouse into Critical");
            require(com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(dead,
                    java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, 20f), 1f, true)
                    == com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED
                    && MobStateSystem.isDead(dead), "typed damage moves normal mouse into Dead");
            helper.runAfterDelay(410, () -> {
                try {
                    require(sounds.isEmpty(), "critical and dead mice do not broadcast ambient squeaks");
                    helper.succeed();
                } finally {
                    NeoForge.EVENT_BUS.unregister(listener);
                }
            });
        } catch (RuntimeException | AssertionError failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            throw failure;
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 260, batch = "npc_ambient_reload")
    public static void unloadClearsPendingAmbientTimer(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        clearPriorTestMice(helper, level);
        var sounds = new ArrayList<PlayLevelSoundEvent>();
        Consumer<PlayLevelSoundEvent> listener = event -> {
            if (event.getLevel() == level && event.getSound() != null
                    && event.getSound().value() == ModSounds.MOUSE_SQUEAK.get()) sounds.add(event);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            var original = spawnMouse(helper, level, new net.minecraft.core.BlockPos(8, 1, 8));
            var id = original.getUUID();
            helper.runAfterDelay(80, () -> {
                original.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
                var restored = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
                require(restored != null, "mouse recreation succeeds after level unload");
                restored.setUUID(id);
                restored.setPos(helper.absolutePos(new net.minecraft.core.BlockPos(8, 1, 8)).getCenter());
                require(level.addFreshEntity(restored), "reloaded mouse rejoins with persistent identity");
                helper.runAfterDelay(155, () -> {
                    try {
                        require(sounds.isEmpty(), "unload removed old pending timer; reload observes a fresh minimum interval");
                        helper.succeed();
                    } finally {
                        NeoForge.EVENT_BUS.unregister(listener);
                    }
                });
            });
        } catch (RuntimeException | AssertionError failure) {
            NeoForge.EVENT_BUS.unregister(listener);
            throw failure;
        }
    }

    private static com.juicyslew.moonstation14.entities.MouseMob spawnMouse(
            GameTestHelper helper, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        level.setBlock(helper.absolutePos(pos).below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var mouse = com.juicyslew.moonstation14.entities.ModEntities.MOUSE.get().create(level);
        require(mouse != null, "normal mouse factory creates entity");
        mouse.setPos(helper.absolutePos(pos).getCenter());
        require(level.addFreshEntity(mouse), "mouse joins through ordinary entity lifecycle");
        return mouse;
    }

    /** GameTest batches share a level, so discard stale mice from already-finished nearby fixtures. */
    private static void clearPriorTestMice(GameTestHelper helper, net.minecraft.server.level.ServerLevel level) {
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(8, 1, 8));
        var nearby = new net.minecraft.world.phys.AABB(origin).inflate(256);
        for (var mouse : level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.MouseMob.class, nearby))
            mouse.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
