package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.shuttle.sable.SableFixturePlot;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.UUID;

/** Permission-2 diagnostics for authoring and observing the real Sable cabin fixture. */
public final class ShuttleFixtureCommands {
    private ShuttleFixtureCommands() { }

    public static void registerCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("shuttle_fixture");
        var build = Commands.literal("build").executes(ctx -> build(ctx.getSource(), null));
        build.then(Commands.argument("origin", BlockPosArgument.blockPos())
                .executes(ctx -> build(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "origin"))));
        root.then(build);
        root.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));
        root.then(Commands.literal("status").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShips(ctx.getSource(), builder))
                .executes(ctx -> status(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ship")))));
        root.then(Commands.literal("nudge").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShips(ctx.getSource(), builder))
                .then(Commands.argument("dx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("dy", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("dz", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .executes(ctx -> nudge(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ship"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dx"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dy"),
                                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "dz"))))))));
        root.then(Commands.literal("yaw").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShips(ctx.getSource(), builder))
                .then(Commands.argument("delta", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .executes(ctx -> yaw(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ship"),
                                com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "delta"))))));
        for (String axis : java.util.List.of("roll","pitch"))
            root.then(Commands.literal(axis).then(Commands.argument("ship",com.mojang.brigadier.arguments.StringArgumentType.word())
                    .suggests((ctx,builder)->suggestShips(ctx.getSource(),builder))
                    .then(Commands.argument("delta",com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                            .executes(ctx -> localAngularDelta(ctx.getSource(),ctx.getArgument("ship",String.class),
                                    ctx.getArgument("delta",Double.class),axis.equals("roll")?2:0,axis)))));
        root.then(Commands.literal("brake").then(Commands.argument("ship",com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx,builder)->suggestShips(ctx.getSource(),builder)).executes(ctx->brake(ctx.getSource(),ctx.getArgument("ship",String.class)))));
        root.then(Commands.literal("cargo").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShips(ctx.getSource(), builder))
                .executes(ctx -> cargo(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ship")))));
        var motion = Commands.literal("motion").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, builder) -> suggestShips(ctx.getSource(), builder))
                .then(Commands.argument("vx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                        .then(Commands.argument("vy", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("vz", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("yaw_rate", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,120))
                                                        .executes(ctx -> motion(ctx.getSource(),ctx.getArgument("ship",String.class),
                                                                ctx.getArgument("vx",Double.class),ctx.getArgument("vy",Double.class),ctx.getArgument("vz",Double.class),
                                                                ctx.getArgument("yaw_rate",Double.class),ctx.getArgument("seconds",Integer.class)))))))));
        root.then(motion);
        root.then(Commands.literal("flight").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx,builder)->suggestShips(ctx.getSource(),builder))
                .executes(ctx->flight(ctx.getSource(),ctx.getArgument("ship",String.class),90))
                .then(Commands.argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,120))
                        .executes(ctx->flight(ctx.getSource(),ctx.getArgument("ship",String.class),ctx.getArgument("seconds",Integer.class))))));
        root.then(Commands.literal("bay_light").then(Commands.argument("ship",com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx,builder)->suggestShips(ctx.getSource(),builder))
                .executes(ctx->bayLight(ctx.getSource(),ctx.getArgument("ship",String.class)))));
        root.then(Commands.literal("motion_stop").then(Commands.argument("ship", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx,builder)->suggestShips(ctx.getSource(),builder)).executes(ctx->motionStop(ctx.getSource(),ctx.getArgument("ship",String.class)))));
        event.getDispatcher().register(Commands.literal("ms14").requires(s -> s.hasPermission(2)).then(root));
    }

    private static int build(net.minecraft.commands.CommandSourceStack source, net.minecraft.core.BlockPos requested) {
        var level = source.getLevel();
        net.minecraft.core.BlockPos pos = requested;
        if (pos == null) {
            if (!(source.getEntity() instanceof ServerPlayer player))
                return fail(source, "Console/non-player source must provide an explicit loaded, clear world origin.");
            var authority = HandActorAuthority.resolve(player).orElse(null);
            if (authority == null) return fail(source, "No authorized controlled body; supply an explicit world origin.");
            var body = authority.body();
            if (body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel()
                    || level.getEntity(body.getUUID()) != body
                    || BodyContainmentService.forLevel(level).ownerOf(body).isPresent())
                return fail(source, "Controlled body must be live, uncontained, and in this dimension; supply an explicit world origin.");
            // Place the cabin three blocks east of the actual controlled body, rather than over its hull.
            pos = body.blockPosition().offset(3, 0, 0);
        }
        try {
            ShuttleCabinFixture.preflight(level, pos, ShuttleManualFixture.WIDTH,
                    ShuttleManualFixture.HEIGHT, ShuttleManualFixture.DEPTH);
            var authored = SableFixturePlot.allocateAndAuthor(level,
                    new Vector3d(pos.getX() + .5, pos.getY(), pos.getZ() + .5), plot -> {
                        var storage = plot.storageOrigin();
                         plot.ensureStorageFootprint(storage, ShuttleManualFixture.WIDTH,
                                 ShuttleManualFixture.HEIGHT, ShuttleManualFixture.DEPTH);
                         return ShuttleManualFixture.build(level, storage, plot.engineId(), plot::setStorageBlock);
                    });
            var plot = authored.plot();
            ShuttleFixtureMotionService.registerFixture(plot.subLevel());
            source.sendSuccess(() -> Component.literal("Built boarding-friendly diagnostic shuttle; open bay at local x=12..14, y=1..3, z=0; " + displayId(level, plot.engineId()) + " (Sable UUID=" + plot.engineId() + ")"
                    + ", dimension=" + level.dimension().location() + ", worldOrigin=" + plot.worldOrigin()
                    + ", storageOrigin=" + plot.storageOrigin() + ". Not body/cargo/flight acceptance."), false);
            return 1;
        } catch (RuntimeException failure) {
            return fail(source, "Cabin build refused/failed without clearing world terrain: " + failure.getMessage());
        }
    }

    private static int status(net.minecraft.commands.CommandSourceStack source, String id) {
        var level = source.getLevel();
        var resolved = resolve(level, id);
        if (!(resolved instanceof ServerSubLevel sub) || sub.isRemoved())
            return fail(source, "Ship ID/UUID is not uniquely loaded in this dimension; run shuttle_fixture list. No force-load attempted: " + id);
        var physics = SubLevelPhysicsSystem.get(level);
        var pose = physics == null ? sub.logicalPose() : physics.getPipeline().readPose(sub,new dev.ryanhcode.sable.companion.math.Pose3d());
        var up = pose.orientation().transform(new Vector3d(0,1,0));
        double tiltDegrees = Double.isFinite(up.x) && Double.isFinite(up.y) && Double.isFinite(up.z)
                ? Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,up.y/Math.max(1e-30,up.length()))))) : Double.NaN;
        var storage = sub.getPlot().getCenterBlock();
        var roomCell = storage.offset(ShuttleCabinFixture.MIN_ROOM_CELL);
        java.util.Optional<AtmosphereReading> room = loaded(level, roomCell)
                ? AtmosphereService.INSTANCE.readAtmosphere(level, roomCell) : java.util.Optional.empty();
        var alarmPos = storage.offset(ShuttleCabinFixture.MACHINE);
        var airlockPos = storage.offset(ShuttleCabinFixture.AIRLOCK);
        var pipePos = storage.offset(ShuttleCabinFixture.PIPE);
        var alarmBE = loaded(level, alarmPos) ? level.getBlockEntity(alarmPos) : null;
        var airlockBE = loaded(level, airlockPos) ? level.getBlockEntity(airlockPos) : null;
        String alarm = alarmBE instanceof AirAlarmBlockEntity be ? PowerRuntime.airAlarmMeter(level, be).toString() : "unknown/unavailable";
        String hatch = airlockBE instanceof AirlockBlockEntity be ? PowerRuntime.airlockPower(level, be).toString() : "unknown/unavailable";
        String pipe = loaded(level, pipePos) ? LoadedPipeGasOwner.activeSnapshot(level, pipePos, VisualPipeBlockEntity.PRIMARY)
                .map(entry -> "owner=" + entry.incarnation() + ", active=" + entry.active() + ", version=" + entry.version()
                        + ", gas=" + mixture(entry.mixture())).orElse("unknown/unavailable") : "unloaded";
        String sdkSubsteps = physics == null ? "unavailable" : Integer.toString(physics.getConfig().substepsPerTick);
        String result = displayId(level, sub.getUniqueId()) + " (Sable UUID=" + sub.getUniqueId() + "), dimension=" + level.dimension().location()
                + ", worldOrigin=" + pose.transformPosition(new Vector3d(storage.getX(), storage.getY(), storage.getZ()))
                + ", storageOrigin=" + storage + ", bodyUp=" + up + ", tiltDegrees=" + tiltDegrees
                + ", room=" + room.map(r -> r.status() + " " + mixture(r.mixture())).orElse("unknown/unavailable")
                + ", alarm100W-path=" + alarm + ", airlock20W-path=" + hatch + ", pipeGas=" + pipe
                + ", sdkSubstepsPerTick=" + sdkSubsteps + ", motion=" + ShuttleFixtureMotionService.status(level, sub)
                + "; body/cargo/FTL/persistence acceptance unproven.";
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int motion(net.minecraft.commands.CommandSourceStack source,String id,double vx,double vy,double vz,double yaw,int seconds) {
        ServerSubLevel sub=resolve(source.getLevel(),id);
        if(sub==null) return fail(source,"Ship is not uniquely loaded here; no force-load attempted.");
        ShuttleMotionRequest request;
        try { request=new ShuttleMotionRequest(vx,vy,vz,yaw,Math.multiplyExact(seconds,20)); }
        catch(RuntimeException invalid) { return fail(source,"Invalid target: finite world velocity (m/s) and world-Y yaw rate (rad/s) required; duration must be 1..120 seconds."); }
        if(!ShuttleFixtureMotionService.start(source.getLevel(),sub,request)) return fail(source,"Motion requires a registered diagnostic fixture with a ready native body and valid mass.");
        source.sendSuccess(()->Component.literal("Bounded physical feedback target set: world velocity ("+vx+", "+vy+", "+vz+") m/s, world-Y yaw "+yaw+" rad/s, duration "+seconds+" seconds. Physical feedback is bounded and may not reach exact targets; no attitude lock or instant stop; gravity/collisions remain active."),false);
        return 1;
    }
    private static int motionStop(net.minecraft.commands.CommandSourceStack source,String id) {
        ServerSubLevel sub=resolve(source.getLevel(),id);
        if(sub==null || !ShuttleFixtureMotionService.stop(source.getLevel(),sub)) return fail(source,"No registered motion controller for that ship.");
        source.sendSuccess(()->Component.literal("Motion controller stopped; native velocity is preserved and stopping is not guaranteed."),false); return 1;
    }
    private static int flight(net.minecraft.commands.CommandSourceStack source,String id,int seconds) {
        ServerSubLevel sub=resolve(source.getLevel(),id);
        if(sub==null) return fail(source,"Flight requires a uniquely loaded native ship; no force-load attempted.");
        // Flight is an explicit permission-2 opt-in: arm exactly the selected live body so an already-built
        // ship need not be rebuilt merely to repopulate this diagnostic-only registration after reload.
        ShuttleFixtureMotionService.registerFixture(sub);
        if(!ShuttleFixtureMotionService.startFreightFlight(source.getLevel(),sub,seconds*20))
            return fail(source,"Flight requires a registered live diagnostic fixture and ready native body.");
        source.sendSuccess(()->Component.literal("Heavy-freight diagnostic flight profile active for "+seconds+"s: smooth native-substep steering, accel caps "+ShuttleFreightFlightProfile.LINEAR_ACCELERATION_LIMIT+"m/s^2 / "+ShuttleFreightFlightProfile.ANGULAR_ACCELERATION_LIMIT+"rad/s^2, jerk caps "+ShuttleFreightFlightProfile.LINEAR_JERK_LIMIT+"m/s^3 / "+ShuttleFreightFlightProfile.ANGULAR_JERK_LIMIT+"rad/s^3. Uses actual merged mass/inertia and existing ideal gravity/drag compensation; this is a mass-normalized Minecraft debug handling adaptation, not fixed-thrust SS14 cargo propulsion. Native collisions remain active; if the profile exceeds its 64m-horizontal/8m-vertical envelope it disengages with native momentum preserved (not an instant brake). Stop with /ms14 shuttle_fixture motion_stop "+id+"."),false);
        return 1;
    }
    private static int bayLight(net.minecraft.commands.CommandSourceStack source,String id) {
        ServerSubLevel sub=resolve(source.getLevel(),id);
        if(sub==null || !ShuttleManualFixture.ensureBayLight(source.getLevel(),sub))
            return fail(source,"Bay light requires a uniquely loaded ship with a valid loaded roof.");
        source.sendSuccess(()->Component.literal("Installed/idempotently repaired bay ceiling light for "+id+"."),false); return 1;
    }

    private static int nudge(net.minecraft.commands.CommandSourceStack source, String id, double dx, double dy, double dz) {
        return applyVelocityDelta(source, id, new Vector3d(dx, dy, dz), new Vector3d(),
                "native linear-velocity delta (m/s) " + new Vec3(dx, dy, dz));
    }

    private static int yaw(net.minecraft.commands.CommandSourceStack source, String id, double delta) {
        return applyVelocityDelta(source, id, new Vector3d(), new Vector3d(0, delta, 0),
                "world-Y angular-velocity delta (rad/s): " + delta);
    }

    private static int localAngularDelta(net.minecraft.commands.CommandSourceStack source,String id,double delta,int axis,String name) {
        var sub=resolve(source.getLevel(),id);
        if(sub==null)return fail(source,"Ship is not uniquely loaded here; no force-load attempted.");
        var physics=SubLevelPhysicsSystem.get(source.getLevel());
        if(physics==null)return fail(source,"Native physics unavailable.");
        var q=physics.getPipeline().readPose(sub,new dev.ryanhcode.sable.companion.math.Pose3d()).orientation();
        Vector3d local=axis==2?new Vector3d(0,0,delta):new Vector3d(delta,0,0);
        q.transform(local);
        return applyVelocityDelta(source,id,new Vector3d(),local,name+" body-local angular-velocity delta (rad/s): "+delta);
    }
    private static int brake(net.minecraft.commands.CommandSourceStack source,String id) {
        var sub=resolve(source.getLevel(),id);
        if(sub==null || !ShuttleFixtureMotionService.queueBrake(source.getLevel(),sub))
            return fail(source,"Brake requires a registered live diagnostic fixture and ready physics service.");
        source.sendSuccess(()->Component.literal("One-shot physical counter-impulse queued; feedback motion released. Pose preserved; gravity and collisions remain active."),false);
        return 1;
    }

    private static int applyVelocityDelta(net.minecraft.commands.CommandSourceStack source, String id,
                                          Vector3d linear, Vector3d angular, String description) {
        if (!finite(linear) || !finite(angular)) return fail(source, "Velocity deltas must be finite.");
        var level = source.getLevel();
        var resolved = resolve(level, id);
        if (resolved == null) return fail(source, "Sable server sublevel UUID is not loaded in this dimension; no force-load attempted: " + id);
        var handle = RigidBodyHandle.of(resolved);
        if (handle == null || !handle.isValid()) return fail(source, "Sable native rigid body is not ready: " + id);
        handle.addLinearAndAngularVelocity(linear, angular);
        source.sendSuccess(() -> Component.literal("Applied diagnostic " + description + " to "
                + displayId(level, resolved.getUniqueId()) + " (" + resolved.getUniqueId() + "); not piloting."), false);
        return 1;
    }

    private static int cargo(net.minecraft.commands.CommandSourceStack source, String id) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return fail(source, "Cargo grant requires an authorized player body aboard the named ship.");
        var snapshot = HandActorAuthority.resolve(player).orElse(null);
        if (snapshot == null || !HandActorAuthority.revalidate(player, snapshot)) return fail(source, "No authorized controlled body.");
        var level = source.getLevel();
        var sub = resolve(level, id);
        if (sub == null) return fail(source, "Ship ID/UUID is not uniquely loaded in this dimension; no force-load attempted: " + id);
        var body = snapshot.body();
        var point = LoadedFrameRayQuery.currentActorPoint(level, body);
        if (point == null || !sub.getUniqueId().equals(point.frameId()) || body.level() != level
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body
                || com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level).ownerOf(body).isPresent()
                || !HandActorAuthority.revalidate(player, snapshot))
            return fail(source, "Controlled body must be live, uncontained, and currently aboard exactly " + id + ".");
        var stack = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get());
        var reagent = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("water");
        // Diagnostic fixture cargo exceeds the >15-unit slippery-solution cutoff; running is still required.
        if (reagent == null || 20f > com.juicyslew.moonstation14.item.ModItems.JUG.get().getCapacity(level))
            return fail(source, "Resolved jug capacity/reagent unavailable; no cargo granted.");
        stack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),
                com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.fromCents(java.util.Map.of(reagent, 2000L)));
        var entity = BodyHandItemTransfer.spawnAuthorizedGrant(player, snapshot, stack, stage ->
                MoonStation14.LOGGER.warn("[shuttle-cargo] grant rejected at stage={} actor={} body={} ship={}",
                        stage, player.getUUID(), body.getUUID(), sub.getUniqueId()));
        if (entity == null) return fail(source, "Could not place cargo safely; no jug was published (see [shuttle-cargo] rejection stage).");
        source.sendSuccess(() -> Component.literal("Granted one water jug (20 units), world item " + entity.getUUID()
                + "; placed near the controlled body. Pick it up normally."), false);
        return 1;
    }

    private static boolean finite(Vector3d v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }

    private static String displayId(ServerLevel level, UUID id) {
        return ShuttleFixtureShipIds.display(loadedIds(level), id);
    }

    private static ServerSubLevel resolve(ServerLevel level, String value) {
        var container = SubLevelContainer.getContainer(level);
        UUID id = ShuttleFixtureShipIds.resolve(loadedIds(level), value);
        var candidate = id == null ? null : container.getSubLevel(id);
        return candidate instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }

    private static java.util.List<UUID> loadedIds(ServerLevel level) {
        return SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                .filter(s -> !s.isRemoved()).map(s -> s.getUniqueId()).toList();
    }

    private static int list(net.minecraft.commands.CommandSourceStack source) {
        var ships = SubLevelContainer.getContainer(source.getLevel()).getAllSubLevels().stream()
                .filter(s -> s instanceof ServerSubLevel && !s.isRemoved()).map(s -> (ServerSubLevel)s)
                .toList();
        source.sendSuccess(() -> Component.literal(ships.isEmpty() ? "No loaded Sable ships in this dimension."
                : "Loaded Sable ships: " + ships.stream().map(s -> displayId(source.getLevel(), s.getUniqueId()) + " (" + s.getUniqueId() + ")")
                .collect(java.util.stream.Collectors.joining(", "))), false);
        return ships.size();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestShips(
            net.minecraft.commands.CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (var candidate : SubLevelContainer.getContainer(source.getLevel()).getAllSubLevels())
            if (candidate instanceof ServerSubLevel sub && !sub.isRemoved()) {
                builder.suggest(displayId(source.getLevel(), sub.getUniqueId()));
                builder.suggest(sub.getUniqueId().toString());
            }
        return builder.buildFuture();
    }

    private static boolean loaded(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk;
    }

    private static String mixture(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture mixture) {
        return "moles=" + mixture.gasMoles() + ", temperatureK=" + mixture.temperatureKelvin()
                + ", volumeM3=" + mixture.volumeCubicMeters();
    }

    private static int fail(net.minecraft.commands.CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
