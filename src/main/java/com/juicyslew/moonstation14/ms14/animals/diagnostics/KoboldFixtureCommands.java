package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Operator-only fresh-world kobold load fixture; owns only its population and placement. */
public final class KoboldFixtureCommands {
    private static final Map<MinecraftServer, Active> ACTIVE = new WeakHashMap<>();
    private static boolean listenerRegistered;
    private record Active(UUID owner, AnimalPopulationFixture.Handle handle) { }
    private KoboldFixtureCommands() { }

    public static void registerCommands(RegisterCommandsEvent event) {
        if (!listenerRegistered) { NeoForge.EVENT_BUS.addListener(KoboldFixtureCommands::serverStopped); listenerRegistered = true; }
        var fixture = Commands.literal("kobold_fixture").requires(s -> s.hasPermission(2))
                .then(Commands.literal("setup").executes(c -> setup(c.getSource(), AnimalPopulationSpec.KOBOLD_PIT.count(), AnimalPopulationSpec.KOBOLD_PIT.width()))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(c -> setup(c.getSource(), IntegerArgumentType.getInteger(c, "count"), AnimalPopulationSpec.KOBOLD_PIT.width()))
                                        .then(Commands.argument("width", IntegerArgumentType.integer(6, 16))
                                        .executes(c -> setup(c.getSource(), IntegerArgumentType.getInteger(c, "count"), IntegerArgumentType.getInteger(c, "width"))))))
        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())));
         var animals = Commands.literal("animal_fixture").requires(s -> s.hasPermission(2))
                .then(Commands.literal("setup").executes(c -> setup(c.getSource(),AnimalPopulationSpec.showcase())))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clear(c.getSource())));
        event.getDispatcher().register(Commands.literal("ms14").requires(s -> s.hasPermission(2)).then(fixture).then(animals));
    }

    private static int setup(net.minecraft.commands.CommandSourceStack source, AnimalPopulationSpec spec) {
        return setupPopulation(source, spec, "Animal");
    }

    private static int setup(net.minecraft.commands.CommandSourceStack source, int count, int width) {
        var spec = new AnimalPopulationSpec(width, AnimalPopulationSpec.HEIGHT, java.util.List.of("kobold"), count);
        return setupPopulation(source, spec, "Kobold");
    }

    private static int setupPopulation(net.minecraft.commands.CommandSourceStack source, AnimalPopulationSpec spec, String label) {
        ServerPlayer player = source.getPlayer();
        var actor = player == null ? null : LifecycleCharacterSessionControl.committedActorSnapshot(player).orElse(null);
        if (actor == null) return fail(source, label + " fixture requires a committed active character body.");
        Active existing = ACTIVE.get(source.getServer());
        if (existing != null) return fail(source, "A fixture is already active; run /ms14 animal_fixture clear first.");
        ServerLevel level = (ServerLevel) actor.body().level();
        var candidate = FixtureSettling.firstPassing(FixtureSettling.candidates(actor.body().position(), actor.body().getYRot(), spec.width()),
                c -> AnimalPopulationFixture.preflight(level, c.origin(), spec) == null);
        if (candidate == null) return fail(source, label + " fixture setup refused: no safe, loaded, empty candidate area near the committed character.");
        var result = AnimalPopulationFixture.create(level, candidate.origin(), spec);
        if (!result.succeeded()) return fail(source, label + " fixture setup refused: " + result.failure());
        ACTIVE.put(source.getServer(), new Active(player.getUUID(), result.handle()));
        var msg = label + " fixture ready: species=" + spec.species() + ", requested=" + spec.count() + ", spawned=" + result.handle().spawnedCount() + ", width=" + spec.width() + ", spacing=" + spec.cellSpacing() + ", origin=" + candidate.origin() + ", normalAI=true, invulnerability=false, damage/cramming and gamerules unchanged.";
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        Active active = ACTIVE.get(source.getServer());
        if (active == null) return fail(source, "No active animal fixture.");
        var h = active.handle();
        var population = h.populationStatus();
        source.sendSuccess(() -> Component.literal("Animal fixture species="+h.spec().species()+" requested=" + h.spec().count() + ", spawned=" + h.spawnedCount() + ", loadedAlive=" + population.loadedAlive() + ", confirmedRemoved=" + population.confirmedRemoved() + ", unresolved=" + population.unresolved() + ", width=" + h.spec().width() + ", spacing="+h.spec().cellSpacing()+", origin=" + h.origin() + ", normalAI=true, gamerules unchanged."), false);
        return 1;
    }

    private static int clear(net.minecraft.commands.CommandSourceStack source) {
        Active active = ACTIVE.get(source.getServer());
        if (active == null) return fail(source, "No active animal fixture.");
        var result = active.handle().cleanup();
        if (!result.unresolvedEntities().isEmpty()) {
            source.sendFailure(Component.literal("Fixture cleanup incomplete: removed=" + result.removedEntities() + ", restoredBlocks=" + result.restoredBlocks() + ", unresolved=" + result.unresolvedEntities().size() + "; ownership retained, load the area and retry."));
            return 0;
        }
        ACTIVE.remove(source.getServer());
        source.sendSuccess(() -> Component.literal("Animal fixture cleared: removed=" + result.removedEntities() + ", restoredBlocks=" + result.restoredBlocks() + "."), false);
        return 1;
    }
    private static int fail(net.minecraft.commands.CommandSourceStack source, String message) { source.sendFailure(Component.literal(message)); return 0; }
    static AnimalPopulationFixture.Handle activeFixture(MinecraftServer server) {
        Active active = ACTIVE.get(server);
        return active == null ? null : active.handle();
    }
    private static void serverStopped(ServerStoppedEvent event) { ACTIVE.remove(event.getServer()); }
}
