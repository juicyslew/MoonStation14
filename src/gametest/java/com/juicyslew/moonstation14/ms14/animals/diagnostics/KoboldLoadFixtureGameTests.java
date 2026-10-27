package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native regression coverage for the operator-facing, owned kobold load fixture. */
@PrefixGameTestTemplate(false)
@GameTestTags({"animals", "kobold-load-fixture"})
public final class KoboldLoadFixtureGameTests {
    private KoboldLoadFixtureGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_power_clear", timeoutTicks = 80)
    @GameTestTags({"animals", "kobold-load-fixture"})
    public static void koboldCommandRequiresCommittedOperatorAndClearsOnlyOwnedResources(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var server = level.getServer();
        var staging = helper.absolutePos(new BlockPos(24, 2, 24));
        var spec = new AnimalPopulationSpec(6, AnimalPopulationSpec.HEIGHT, java.util.List.of("kobold"), 4);
        var candidates = FixtureSettling.candidates(new net.minecraft.world.phys.Vec3(staging.getX() + .5,
                staging.getY(), staging.getZ() + .5), 0, spec.width());
        BlockPos arenaMin = staging, arenaMax = staging;
        for (var candidate : candidates) {
            var max = candidate.origin().offset(spec.width() - 1, AnimalPopulationSpec.HEIGHT - 1, spec.width() - 1);
            arenaMin = new BlockPos(Math.min(arenaMin.getX(), candidate.origin().getX()),
                    Math.min(arenaMin.getY(), candidate.origin().getY()), Math.min(arenaMin.getZ(), candidate.origin().getZ()));
            arenaMax = new BlockPos(Math.max(arenaMax.getX(), max.getX()), Math.max(arenaMax.getY(), max.getY()),
                    Math.max(arenaMax.getZ(), max.getZ()));
        }
        var tickets = FixtureChunkTickets.around(helper, arenaMin, arenaMax, 0);
        FixtureChunkTickets.awaitEntityTicking(helper, tickets, 20, () -> {
        var playerFixture = RegisteredServerPlayerFixture.register(level, "koboldfixture");
        try {
            helper.runAtTickTime(2, () -> {
                AnimalPopulationFixture.Handle ownedHandle = null;
                net.minecraft.world.entity.Entity foreign = null;
                BlockPos editedPos = null;
                var editedOriginal = Blocks.AIR.defaultBlockState();
                try {
                    var player = playerFixture.player();
                    var source = player.createCommandSourceStack().withPermission(0);
                    require(denied(server, source, "ms14 kobold_fixture setup 4 6") && KoboldFixtureCommands.activeFixture(server) == null,
                            "uncommitted, non-operator source cannot create fixture");
                    require(execute(server, player.createCommandSourceStack().withPermission(2), "ms14 kobold_fixture setup 4 6") == 0
                                    && KoboldFixtureCommands.activeFixture(server) == null,
                            "uncommitted operator source cannot create fixture");

                    var active = RegisteredPlayerReadyDriver.readyAndQuery(playerFixture);
                    active.body().teleportTo(staging.getX() + .5, staging.getY(), staging.getZ() + .5);
                    active.body().setYRot(0);
                    active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    source = player.createCommandSourceStack().withPermission(2);
                    var setupFeedback = capture(source);
                    int setupResult = execute(server, setupFeedback.stack(), "ms14 kobold_fixture setup 4 6");
                    require(setupResult == 1, "committed operator sets up requested fixture: result=" + setupResult
                            + ", feedback=" + setupFeedback.feedback());
                    var handle = KoboldFixtureCommands.activeFixture(server);
                    ownedHandle = handle;
                    require(handle != null && handle.spawnedCount() == 4 && handle.spec().count() == 4
                                    && handle.spec().width() == 6 && handle.spec().speciesAt(0).equals("kobold"),
                            "active fixture is precisely the requested kobold-only population");
                    for (var id : handle.animalIds()) {
                        var entity = handle.level().getEntity(id);
                        require(entity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive() && !mob.isNoAi() && !mob.isInvulnerable()
                                        && mob.getType() == com.juicyslew.moonstation14.entities.AnimalMobRegistration.types().get("kobold").get(),
                                "fixture contains a live, normally registered kobold with ordinary AI/damage");
                    }
                    require(execute(server, source, "ms14 kobold_fixture setup 4 6") == 0
                                    && KoboldFixtureCommands.activeFixture(server) == handle,
                            "duplicate setup is refused without replacing active ownership");
                    require(execute(server, source, "ms14 kobold_fixture status") == 1,
                            "status reports active owned fixture");

                    var killed = handle.level().getEntity(handle.animalIds().get(0));
                    require(killed != null, "owned kobold available for killed-removal regression");
                    killed.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    var afterDeath = handle.populationStatus();
                    require(afterDeath.confirmedRemoved() == 1 && afterDeath.unresolved() == 0,
                            "known killed entity is confirmed removed rather than unresolved");

                    var change = handle.changes().get(0);
                    editedPos = change.pos();
                    editedOriginal = change.originalState();
                    level.setBlock(editedPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                    foreign = EntityType.COW.create(level);
                    require(foreign != null, "foreign test entity factory available");
                    foreign.moveTo(handle.origin().getX() + 2.5, handle.origin().getY() + 1,
                            handle.origin().getZ() + 2.5, 0, 0);
                    require(level.addFreshEntity(foreign), "foreign entity admitted");

                    require(execute(server, source, "ms14 kobold_fixture clear") == 1
                                    && KoboldFixtureCommands.activeFixture(server) == null,
                            "clear succeeds after one normally killed animal and releases ownership");
                    for (var id : handle.animalIds()) require(handle.level().getEntity(id) == null,
                            "clear removes every tracked fixture animal");
                    require(foreign.isAlive() && level.getBlockState(editedPos).is(Blocks.OBSIDIAN),
                            "clear preserves foreign entity and owner-edited placed block");
                    for (var changeOwned : handle.changes()) if (!changeOwned.pos().equals(editedPos))
                        require(level.getBlockState(changeOwned.pos()).equals(changeOwned.originalState()),
                                "clear restores unchanged fixture-owned placement");
                    helper.succeed();
                } finally {
                    if (ownedHandle != null && KoboldFixtureCommands.activeFixture(server) == ownedHandle)
                        execute(server, playerFixture.player().createCommandSourceStack().withPermission(2), "ms14 kobold_fixture clear");
                    if (foreign != null && !foreign.isRemoved()) foreign.discard();
                    if (editedPos != null && level.getBlockState(editedPos).is(Blocks.OBSIDIAN)) level.setBlock(editedPos, editedOriginal, 3);
                    playerFixture.close();
                }
            });
        } catch (RuntimeException | Error failure) {
            playerFixture.close();
            throw failure;
        }
        }, candidates.stream().flatMap(candidate -> java.util.stream.Stream.of(candidate.origin(),
                candidate.origin().offset(spec.width() - 1, AnimalPopulationSpec.HEIGHT - 1, spec.width() - 1))).toArray(BlockPos[]::new));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 80, batch = "animal_showcase_command")
    @GameTestTags({"animals", "kobold-load-fixture", "animal-showcase-fixture"})
    public static void animalShowcaseFixtureUsesCatalogAndSharedOwnership(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        var server = level.getServer();
        var staging = helper.absolutePos(new BlockPos(8, 16, 8));
        var showcaseSpec = AnimalPopulationSpec.showcase();
        var stagedFeet = new net.minecraft.world.phys.Vec3(staging.getX() + 0.5, staging.getY(), staging.getZ() + 0.5);
        var candidates = FixtureSettling.candidates(stagedFeet, 0, showcaseSpec.width());
        BlockPos arenaMin = staging;
        BlockPos arenaMax = staging;
        var tickingPositions = new java.util.ArrayList<BlockPos>();
        tickingPositions.add(staging);
        for (var candidate : candidates) {
            var candidateMax = candidate.origin().offset(showcaseSpec.width() - 1, AnimalPopulationSpec.HEIGHT - 1,
                    showcaseSpec.width() - 1);
            tickingPositions.add(candidate.origin());
            tickingPositions.add(candidateMax);
            arenaMin = new BlockPos(Math.min(arenaMin.getX(), candidate.origin().getX()),
                    Math.min(arenaMin.getY(), candidate.origin().getY()), Math.min(arenaMin.getZ(), candidate.origin().getZ()));
            arenaMax = new BlockPos(Math.max(arenaMax.getX(), candidateMax.getX()),
                    Math.max(arenaMax.getY(), candidateMax.getY()), Math.max(arenaMax.getZ(), candidateMax.getZ()));
        }
        var arenaTickets = FixtureChunkTickets.around(helper, arenaMin, arenaMax, 0);
        FixtureChunkTickets.awaitEntityTicking(helper, arenaTickets, 20, () -> {
            var playerFixture = RegisteredServerPlayerFixture.register(level, "animalshowcase");
            try {
                helper.runAfterDelay(2, () -> {
                AnimalPopulationFixture.Handle ownedHandle = null;
                net.minecraft.world.entity.Entity foreign = null;
                BlockPos editedPos = null;
                var editedOriginal = Blocks.AIR.defaultBlockState();
                try {
                    var player = playerFixture.player();
                    var command = "ms14 animal_fixture setup";
                    var source = player.createCommandSourceStack().withPermission(0);
                    require(denied(server, source, command) && KoboldFixtureCommands.activeFixture(server) == null,
                            "non-operator cannot create showcase fixture");
                    source = player.createCommandSourceStack().withPermission(2);
                    require(execute(server, source, command) == 0 && KoboldFixtureCommands.activeFixture(server) == null,
                            "uncommitted operator cannot create showcase fixture");

                    var active = RegisteredPlayerReadyDriver.readyAndQuery(playerFixture);
                    active.body().teleportTo(staging.getX() + 0.5, staging.getY(), staging.getZ() + 0.5);
                    active.body().setYRot(0);
                    active.body().setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    source = player.createCommandSourceStack().withPermission(2);
                    var setupFeedback = capture(source);
                    int setupResult = execute(server, setupFeedback.stack(), command);
                    require(setupResult == 1, "committed operator creates showcase fixture: result=" + setupResult
                            + ", feedback=" + setupFeedback.feedback());
                    var handle = KoboldFixtureCommands.activeFixture(server);
                    ownedHandle = handle;
                    var catalog = com.juicyslew.moonstation14.ms14.animals.AnimalCatalog.entries();
                    int expectedShowcaseCount = catalog.size() + 1;
                    require(handle != null && handle.spawnedCount() == expectedShowcaseCount && handle.spec().count() == expectedShowcaseCount
                                    && handle.spec().width() == 24 && handle.spec().cellSpacing() == 3,
                            "showcase requests one animal per catalog entry plus legacy mouse at width 24 and spacing 3");
                    var ids = new java.util.HashSet<String>();
                    var mobs = new java.util.ArrayList<net.minecraft.world.entity.Mob>();
                    var registeredTypes = com.juicyslew.moonstation14.entities.AnimalMobRegistration.types();
                    for (var entry : catalog) {
                        var type = registeredTypes.get(entry.modelId());
                        require(type != null && ids.add(entry.id()), "catalog model id has a unique registered host");
                        long matches = handle.animalIds().stream().map(handle.level()::getEntity)
                                .filter(entity -> entity instanceof net.minecraft.world.entity.Mob mob && mob.getType() == type.get()).count();
                        require(matches == 1, "showcase contains exactly one registered entity for catalog entry " + entry.id());
                    }
                    var mouseType = com.juicyslew.moonstation14.ms14.animals.diagnostics.AnimalPopulationSpec.showcaseType("mouse");
                    long mouseMatches = handle.animalIds().stream().map(handle.level()::getEntity)
                            .filter(entity -> entity != null && entity.getType() == mouseType).count();
                    require(mouseMatches == 1, "showcase contains exactly one legacy registered mouse entity");
                    for (var id : handle.animalIds()) {
                        var entity = handle.level().getEntity(id);
                        require(entity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive() && !mob.isInvulnerable(),
                                "showcase entity retains life and damage: id=" + id + ", entity=" + entity);
                        var mob = (net.minecraft.world.entity.Mob) entity;
                        if (mob.getType() == mouseType) {
                            // MouseMob's canonical constructor disables vanilla AI; the resolved NPC system owns its behavior.
                            require(mob instanceof com.juicyslew.moonstation14.entities.MouseMob && mob.isNoAi()
                                            && com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob)
                                                .flatMap(character -> character.component(com.juicyslew.moonstation14.ms14.character.components.NpcBehaviorComponent.class)).isPresent(),
                                    "legacy mouse keeps canonical NoAI with resolved NPC behavior: id=" + id
                                            + ", noAi=" + mob.isNoAi() + ", type=" + mob.getType());
                        } else {
                            require(!mob.isNoAi(), "custom showcase entity retains vanilla AI: id=" + id
                                    + ", noAi=" + mob.isNoAi() + ", type=" + mob.getType());
                        }
                        mobs.add((net.minecraft.world.entity.Mob) entity);
                    }
                    for (int i = 0; i < mobs.size(); i++) for (int j = i + 1; j < mobs.size(); j++) {
                        var a = mobs.get(i).position(); var b = mobs.get(j).position();
                        require(a.distanceToSqr(b.x, a.y, b.z) >= 9.0,
                                "initial horizontal animal positions are at least three blocks apart");
                    }
                    require(execute(server, source, "ms14 kobold_fixture status") == 1,
                            "kobold status observes shared active showcase ownership");
                    require(execute(server, source, "ms14 kobold_fixture setup 4 6") == 0
                                    && KoboldFixtureCommands.activeFixture(server) == handle,
                            "kobold setup refuses to replace showcase ownership");
                    var change = handle.changes().get(0);
                    editedPos = change.pos(); editedOriginal = change.originalState();
                    level.setBlock(editedPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                    foreign = EntityType.COW.create(level);
                    require(foreign != null, "foreign entity factory available");
                    foreign.moveTo(handle.origin().getX() + 2.5, handle.origin().getY() + 1,
                            handle.origin().getZ() + 2.5, 0, 0);
                    require(level.addFreshEntity(foreign), "foreign entity admitted");
                    require(execute(server, source, "ms14 kobold_fixture clear") == 1
                                    && KoboldFixtureCommands.activeFixture(server) == null,
                            "either fixture command clears shared ownership");
                    for (var id : handle.animalIds()) require(handle.level().getEntity(id) == null, "clear removes tracked showcase entities");
                    require(foreign.isAlive() && level.getBlockState(editedPos).is(Blocks.OBSIDIAN),
                            "clear preserves foreign cow and owner-edited block");
                    for (var changeOwned : handle.changes()) if (!changeOwned.pos().equals(editedPos))
                        require(level.getBlockState(changeOwned.pos()).equals(changeOwned.originalState()), "clear restores unchanged owned placement");
                    helper.succeed();
                } finally {
                    if (ownedHandle != null && KoboldFixtureCommands.activeFixture(server) == ownedHandle)
                        execute(server, playerFixture.player().createCommandSourceStack().withPermission(2), "ms14 animal_fixture clear");
                    if (foreign != null && !foreign.isRemoved()) foreign.discard();
                    if (editedPos != null && level.getBlockState(editedPos).is(Blocks.OBSIDIAN)) level.setBlock(editedPos, editedOriginal, 3);
                    playerFixture.close();
                }
                });
            } catch (RuntimeException | Error failure) {
                playerFixture.close();
                throw failure;
            }
        }, tickingPositions.toArray(BlockPos[]::new));
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 30)
    @GameTestTags({"animals", "kobold-load-fixture"})
    public static void populationPreflightRejectsObstructedKoboldAreaWithoutMutation(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(20, 1, 20));
        var spec = new AnimalPopulationSpec(6, AnimalPopulationSpec.HEIGHT, java.util.List.of("kobold"), 4);
        BlockPos obstruction = origin.offset(2, 3, 2);
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
        try {
            require(AnimalPopulationFixture.prepare(level, origin, spec).failure() != null,
                    "preflight refuses an obstructed bounded population area");
            require(level.getBlockState(obstruction).is(Blocks.STONE)
                            && level.getBlockState(origin).isAir()
                            && level.getEntities((net.minecraft.world.entity.Entity) null,
                                new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(), origin.getZ(),
                                        origin.getX() + 6, origin.getY() + 5, origin.getZ() + 6), e -> true).isEmpty(),
                    "failed preflight changes no fixture blocks and creates no animals");
            helper.succeed();
        } finally {
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static int execute(net.minecraft.server.MinecraftServer server,
                               net.minecraft.commands.CommandSourceStack source, String command) {
        try { return server.getCommands().getDispatcher().execute(command, source); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new GameTestAssertException("unexpected command syntax failure: " + exception.getMessage());
        }
    }

    private static boolean denied(net.minecraft.server.MinecraftServer server,
                                  net.minecraft.commands.CommandSourceStack source, String command) {
        try { return server.getCommands().getDispatcher().execute(command, source) == 0; }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { return true; }
    }

    private record CapturedSource(net.minecraft.commands.CommandSourceStack stack, java.util.List<String> feedback) { }

    private static CapturedSource capture(net.minecraft.commands.CommandSourceStack original) {
        var messages = new java.util.ArrayList<String>();
        net.minecraft.commands.CommandSource wrapper = new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CapturedSource(original.withSource(wrapper), messages);
    }
}
