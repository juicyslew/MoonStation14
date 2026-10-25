package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Bounded admin construction and entry helper for the botany fixture. */
public final class BotanyFixtureCommands {
    private static final int WIDTH = SmallBotanyFixtureLayout.WIDTH, HEIGHT = SmallBotanyFixtureLayout.HEIGHT, DEPTH = SmallBotanyFixtureLayout.DEPTH;
    private static final Map<net.minecraft.server.MinecraftServer, Map<UUID, Site>> SITES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<net.minecraft.server.MinecraftServer, Map<UUID, Pending>> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean tickListenerRegistered;

    private record Site(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos origin, UUID body, long epoch, String selected) { }
    private record Pending(UUID body, long epoch, Site site, int ticks, net.minecraft.commands.CommandSourceStack source) { }

    private BotanyFixtureCommands() { }

    public static void registerCommands(RegisterCommandsEvent event) {
        if (!tickListenerRegistered) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BotanyFixtureCommands::onPostServerTick);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BotanyFixtureCommands::onServerStopping);
            tickListenerRegistered = true;
        }
        var fixture = Commands.literal("botany_fixture")
                .executes(c -> setup(c.getSource()))
                .then(Commands.literal("status").executes(c -> runStatus(c.getSource())))
                .then(Commands.literal("pause").executes(c -> pacing(c.getSource(), "pause", 0, 0)))
                .then(Commands.literal("resume").executes(c -> pacing(c.getSource(), "resume", 1, 52)))
                .then(Commands.literal("start").then(Commands.argument("set", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> { String name=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"set"); var set=BotanyFixturePacing.named(name); if(set!=null)return pacing(c.getSource(),name,set.first(),set.last()); try { int number=Integer.parseInt(name); return pacing(c.getSource(),"range",number,number); } catch(NumberFormatException ignored) { return fail(c.getSource(),"Unknown small station set; numeric station must be 1..52."); } })
                        .then(Commands.argument("to", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,52))
                                .executes(c -> { int from; try { from=Integer.parseInt(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"set")); } catch(NumberFormatException e) { return fail(c.getSource(),"Numeric start range must be start <from> [to]."); } return pacing(c.getSource(),"range",from,com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"to")); }))));
        event.getDispatcher().register(Commands.literal("ms14").requires(s -> s.hasPermission(2)).then(fixture));
    }

    private static int setup(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        var authority = actor == null ? null : HandActorAuthority.resolve(actor).orElse(null);
        if (authority == null) { source.sendFailure(Component.literal("Botany fixture requires an online player with an authorized character body.")); return 0; }
        Map<UUID, Pending> pending = PENDING.computeIfAbsent(source.getServer(), ignored -> new java.util.HashMap<>());
        if (pending.containsKey(actor.getUUID())) { source.sendFailure(Component.literal("Botany fixture setup is already pending.")); return 0; }
        BlockPos origin = authority.body().blockPosition().offset(2, 0, 2);
        String built = build(source.getLevel(), origin);
        if (!built.startsWith("Built botany fixture")) { source.sendFailure(Component.literal(built)); return 0; }
        Site site = new Site(source.getLevel().dimension(), origin, authority.body().getUUID(), authority.epoch(), "none");
        SITES.computeIfAbsent(source.getServer(), ignored -> new java.util.HashMap<>()).put(actor.getUUID(), site);
        pending.put(actor.getUUID(), new Pending(authority.body().getUUID(), authority.epoch(), site, 0, source));
        source.sendSuccess(() -> Component.literal("Botany fixture setup accepted; room, finite air, body entry, supplies, and tray scenarios are being initialized."), false);
        return 1;
    }

    public static void onPostServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        Map<UUID, Pending> pending = PENDING.get(server);
        if (pending == null) return;
        for (var entry : new ArrayList<>(pending.entrySet())) {
            UUID playerId = entry.getKey(); Pending task = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) { pending.remove(playerId); continue; }
            var site = task.site();
            var authority = HandActorAuthority.resolve(player).orElse(null);
            if (authority == null || !authority.body().getUUID().equals(task.body()) || authority.epoch() != task.epoch() || !player.serverLevel().dimension().equals(site.dimension())) {
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup stopped: authorized character or dimension changed.")); continue;
            }
            ServerLevel level = player.serverLevel();
            if (level.getChunkSource().getChunk(site.origin().getX() >> 4, site.origin().getZ() >> 4, ChunkStatus.FULL, false) == null) {
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: fixture room is no longer loaded; partial room remains.")); continue;
            }
            if (task.ticks() < 2) { pending.put(playerId, new Pending(task.body(), task.epoch(), site, task.ticks() + 1, task.source())); continue; }
            String air = initialize(level, site.origin());
            if (!air.startsWith("Initialized")) {
                if (task.ticks() + 1 < 20) { pending.put(playerId, new Pending(task.body(), task.epoch(), site, task.ticks() + 1, task.source())); continue; }
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: " + air + " Partial room remains.")); continue;
            }
            if (!traysInitialized(level, site.origin())) {
                if (task.ticks() + 1 < 20) { pending.put(playerId, new Pending(task.body(), task.epoch(), site, task.ticks() + 1, task.source())); continue; }
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: tray initialization did not complete within 20 ticks. Partial room remains.")); continue;
            }
            BotanyFixturePacing.pauseAll(level, site.origin());
            String scenarios = com.juicyslew.moonstation14.ms14.botany.BotanyFixtureScenarios.arrange(level, site.origin());
            if (!scenarios.startsWith("Arranged")) {
                if (task.ticks() + 1 < 20) { pending.put(playerId, new Pending(task.body(), task.epoch(), site, task.ticks() + 1, task.source())); continue; }
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: " + scenarios + " Partial room remains.")); continue;
            }
            String moved = enter(player, authority, level, site.origin());
            if (!moved.startsWith("Moved existing")) { pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: " + moved + " Partial room remains.")); continue; }
            // Kit uses the existing body-authority grant path; it is deliberately the final, non-retried step.
            if (!BotanyFixtureSupplies.publish(player, authority, level, site.origin())) { pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed while publishing supplies; partial room/body entry remains.")); continue; }
            pending.remove(playerId);
            task.source().sendSuccess(() -> Component.literal("Botany fixture ready in standby; supplies placed locally. All trays start paused. Use /ms14 botany_fixture start <small-set> or start <from> [to] to begin; pause/resume controls the selected set. Follow numbered signs in walking order."), false);
        }
        if (pending.isEmpty()) PENDING.remove(server);
    }

    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        PENDING.remove(event.getServer());
        SITES.remove(event.getServer());
    }

    private static boolean traysInitialized(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : List.of(SmallBotanyFixtureLayout.normalTray(origin, false), SmallBotanyFixtureLayout.normalTray(origin, true),
                SmallBotanyFixtureLayout.dryTray(origin, false), SmallBotanyFixtureLayout.dryTray(origin, true),
                SmallBotanyFixtureLayout.yieldTray(origin), SmallBotanyFixtureLayout.potencyTray(origin),
                SmallBotanyFixtureLayout.waterTray(origin), SmallBotanyFixtureLayout.nutrientTray(origin),
                SmallBotanyFixtureLayout.untouchedTray(origin), SmallBotanyFixtureLayout.statsMutationTray(origin),
                SmallBotanyFixtureLayout.gasExchangeTray(origin), SmallBotanyFixtureLayout.samplingTray(origin),
                SmallBotanyFixtureLayout.fastStageTray(origin),SmallBotanyFixtureLayout.enduranceTray(origin),
                SmallBotanyFixtureLayout.idealHeatTray(origin),
                SmallBotanyFixtureLayout.lowPressureTray(SmallBotanyFixtureLayout.careOrigin(origin))))
            if (!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray)
                    || !tray.isLoadedValid() || tray.plantState() == null) return false;
        for (int i=0;i<11;i++) if (!(level.getBlockEntity(SmallBotanyFixtureLayout.careTray(SmallBotanyFixtureLayout.careOrigin(origin),i))
                instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray)
                || !tray.isLoadedValid() || tray.plantState()==null) return false;
        if (!(level.getBlockEntity(SmallBotanyFixtureLayout.earlyFruitTray(SmallBotanyFixtureLayout.careOrigin(origin)))
                instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray)
                || !tray.isLoadedValid() || tray.plantState()==null) return false;
        if (!(level.getBlockEntity(SmallBotanyFixtureLayout.lowPressureTray(SmallBotanyFixtureLayout.careOrigin(origin)))
                instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity lowPressureTray)
                || !lowPressureTray.isLoadedValid() || lowPressureTray.plantState()==null) return false;
        for(int i=0;i<8;i++) if(!(level.getBlockEntity(SmallBotanyFixtureLayout.toleranceTray(SmallBotanyFixtureLayout.careOrigin(origin),i))
                instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity toleranceTray)||!toleranceTray.isLoadedValid()||toleranceTray.plantState()==null) return false;
         for(int i=0;i<SmallBotanyFixtureLayout.SHOWCASE_STATIONS;i++) if(!(level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin,i)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity showcaseTray)||!showcaseTray.isLoadedValid()||showcaseTray.plantState()==null) return false;
        if(!(level.getBlockEntity(SmallBotanyFixtureLayout.showcaseLamp(origin)) instanceof PowerDeviceBlockEntity)) return false;
        return true;
    }

    // Resolved packet catalog check over canonical fixture supply definitions.
    static List<net.minecraft.resources.ResourceLocation> coreKitPackets(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.botany.SeedProfile> catalog) {
        List<net.minecraft.resources.ResourceLocation> packets=new ArrayList<>();
        for(var supply:BotanyFixtureSupplies.descriptors()) if(supply.kind()==BotanyFixtureSupplies.Kind.SEED) {
            var profile=catalog.get(ModSeedProfiles.seedId(supply.value()));
            if(profile==null) throw new IllegalStateException("Missing required baseline seed profile: "+supply.value());
            packets.add(profile.packet());
        }
        return List.copyOf(packets);
    }

    private static int runStatus(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        UUID key = actor == null ? null : actor.getUUID();
        Site site = key == null ? null : SITES.computeIfAbsent(source.getServer(), ignored -> new java.util.HashMap<>()).get(key);
        var authority=actor==null?null:HandActorAuthority.resolve(actor).orElse(null);
        boolean available = site != null && site.dimension().equals(source.getLevel().dimension()) && validSite(actor,authority,site);
        String result = !available
                ? "No botany fixture remembered in this dimension; run /ms14 botany_fixture first."
                : "PACING IS TRANSIENT (fixture reload resets holds); selected="+site.selected()+".\n"+BotanyFixturePacing.pausedStatus(source.getLevel(),site.origin())+"\nCommands: start <named set>, start <1..52> [to], pause, resume\n"+status(source.getLevel(), site.origin());
        final String message = result;
        source.sendSuccess(() -> Component.literal(message), false);
        return available ? 1 : 0;
    }

    private static int fail(net.minecraft.commands.CommandSourceStack source,String message) { source.sendFailure(Component.literal(message)); return 0; }
    private static int pacing(net.minecraft.commands.CommandSourceStack source,String set,int first,int last) {
        ServerPlayer actor=source.getPlayer(); UUID key=actor==null?null:actor.getUUID();
        if(key!=null&&PENDING.getOrDefault(source.getServer(),Map.of()).containsKey(key))
            return fail(source,"Botany fixture setup is pending; pacing commands are unavailable until setup completes.");
        Site site=key==null?null:SITES.getOrDefault(source.getServer(),Map.of()).get(key);
        var authority=actor==null?null:HandActorAuthority.resolve(actor).orElse(null);
        if(site==null||!site.dimension().equals(source.getLevel().dimension())||!validSite(actor,authority,site)) return fail(source,"No remembered botany fixture for this authorized body/epoch/dimension; run /ms14 botany_fixture first.");
        ServerLevel level=source.getLevel();
        if(level.getChunkSource().getChunk(site.origin().getX()>>4,site.origin().getZ()>>4,ChunkStatus.FULL,false)==null) return fail(source,"Fixture origin is not loaded; no pacing change made.");
        if(!set.equals("pause")&&!set.equals("resume")&&(first<1||last>52||first>last))
            return fail(source,"Station range must be bounded 1..52 with from <= to.");
        if(set.equals("pause")) BotanyFixturePacing.pauseAll(level,site.origin());
        else if(set.equals("resume")) {
            if(site.selected().equals("none")) return fail(source,"No selected station set. Start a named set or numeric range first.");
            var selected=BotanyFixturePacing.named(site.selected());
            if(selected!=null) BotanyFixturePacing.activate(level,site.origin(),selected);
            else { String[] bounds=site.selected().split("-"); BotanyFixturePacing.activate(level,site.origin(),Integer.parseInt(bounds[0]),Integer.parseInt(bounds[1])); }
        } else {
            var selected=BotanyFixturePacing.named(set);
            if(selected!=null) BotanyFixturePacing.activate(level,site.origin(),selected); else BotanyFixturePacing.activate(level,site.origin(),first,last);
            site=new Site(site.dimension(),site.origin(),site.body(),site.epoch(),selected==null?first+"-"+last:set);
            SITES.get(source.getServer()).put(key,site);
        }
        String selected=set.equals("pause")?"paused; selected="+site.selected():set.equals("resume")?"resumed "+site.selected():set+" "+first+(first==last?"":"-"+last);
        String message="Botany fixture set: "+selected+"; "+BotanyFixturePacing.pausedStatus(level,site.origin())+". Use pause/resume or start <set/range>.";
        source.sendSuccess(()->Component.literal(message),false);
        return 1;
    }
    private static boolean validSite(ServerPlayer actor,HandActorAuthority.Snapshot authority,Site site) {
        return actor!=null&&authority!=null&&authority.body().getUUID().equals(site.body())&&authority.epoch()==site.epoch()
                &&HandActorAuthority.revalidate(actor,authority);
    }

    private static String initialize(ServerLevel level, BlockPos origin) {
        BlockPos wing=SmallBotanyFixtureLayout.careOrigin(origin);
        java.util.LinkedHashSet<BlockPos> cells=new java.util.LinkedHashSet<>();
        for(BlockPos room:SmallBotanyFixtureLayout.roomOrigins(origin)) collectRoomCells(level,room,cells);
        for(var cell:SmallBotanyFixtureLayout.connectorPlan(origin)) if(cell.opening()&&AtmosphereTopology.isPassable(level,cell.pos())) cells.add(cell.pos());
        GasMixture air = new GasMixture(Map.of(GasType.OXYGEN,8.0,GasType.NITROGEN,30.0),293.15);
        StringBuilder rejection = new StringBuilder();
        if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,List.copyOf(cells),air,
                reason -> rejection.append(reason)))
            return "Initialization not ready: bounded finite enclosure rejected ("+rejection+"). No gas written.";
        return "Initialized "+cells.size()+" enclosed finite owned room/headspace cells with 8 mol O2 + 30 mol N2 per cell at 293.15 K.";
    }

    private static void collectRoomCells(ServerLevel level,BlockPos origin,java.util.Set<BlockPos> cells) {
        for(int x=1;x<WIDTH-1;x++) for(int y=1;y<HEIGHT-1;y++) for(int z=1;z<DEPTH-1;z++) {
            BlockPos p=origin.offset(x,y,z);
            if(AtmosphereTopology.isPassable(level,p)) cells.add(p);
        }
    }

    private static String status(ServerLevel level, BlockPos origin) {
        BlockPos sample=SmallBotanyFixtureLayout.sample(origin);
        BlockPos grinderPos=SmallBotanyFixtureLayout.grinder(origin);
        var grinder=level.getBlockEntity(grinderPos) instanceof com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity machine ? PowerRuntime.processingMeter(level,machine) : null;
        BlockPos extractorPos=SmallBotanyFixtureLayout.extractor(origin);
        var extractor=level.getBlockEntity(extractorPos) instanceof com.juicyslew.moonstation14.ms14.botany.SeedExtractorBlockEntity machine ? PowerRuntime.processingMeter(level,machine) : null;
        BlockPos apcPos=SmallBotanyFixtureLayout.apc(origin);
        var apc=level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity device ? device : null;
        var mutationTray = level.getBlockEntity(SmallBotanyFixtureLayout.untouchedTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray ? tray : null;
        var mutationPlant = mutationTray == null ? null : mutationTray.plantState();
        var mutationProfile = mutationPlant == null ? null : mutationPlant.effectiveProfile();
        var acquired = mutationProfile == null ? List.<String>of() : mutationProfile.chemicals().entrySet().stream()
                .filter(entry -> !entry.getValue().inherent()).map(entry -> entry.getKey().toString()).sorted().toList();
        String chemicalStatus = "H CHEMISTRY — "+(mutationPlant == null ? "WAITING: tray unavailable"
                : acquired.isEmpty() ? "WAITING: Vestine metabolism has not added chemistry"
                : "CHANGED: acquired "+acquired);
        var statsTray = level.getBlockEntity(SmallBotanyFixtureLayout.statsMutationTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray ? tray : null;
        var statsPlant = statsTray == null ? null : statsTray.plantState();
        var statsProfile = statsPlant == null ? null : statsPlant.effectiveProfile();
        var mutagen = ModReagents.createKey("unstablemutagen");
        long mutagenCents = statsTray == null ? 0 : statsTray.soilSolution().snapshotUnits().getOrDefault(mutagen, 0L);
        String statsStatus = "I STATS — preset modifier 3 (arranged, not a Left4Zed result): "+(statsPlant == null ? "WAITING: tray unavailable"
                : statsProfile != null && !statsProfile.lifecycle().viable() ? "CHANGED: plant is not viable"
                : "WAITING: first mutation cycle; plant "+(statsProfile == null ? "unknown" : statsProfile.lifecycle().viable() ? "viable" : "not viable"));
         String gasStatus = gasStationStatus(level, SmallBotanyFixtureLayout.gasExchangeTray(origin), "J GAS — preset gas genes (not a random mutation): ");
         String showcaseStatus = java.util.stream.IntStream.range(0, SmallBotanyFixtureLayout.SHOWCASE_STATIONS).mapToObj(i -> {
             var tray = level.getBlockEntity(SmallBotanyFixtureLayout.showcaseTray(origin, i)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
             var plant = tray == null ? null : tray.plantState();
             String name = String.format(java.util.Locale.ROOT, "%c%c", 'A' + (14 + i) / 26, 'A' + (14 + i) % 26);
             String line = name + " — " + (plant == null || plant.seed() == null ? "WAITING: tray/plant unavailable"
                     : i < 6 ? (plant.dead() ? "DEAD" : "ALIVE")
                     : i < 10 ? (plant.ready() ? "READY" : "NOT READY")
                     : "preset gas genes (not acquired mutation): " + gasReportStatus(tray.gasExchangeReport()));
             return line;
         }).collect(java.util.stream.Collectors.joining("\n"));
        var noRepeat = level.getBlockEntity(SmallBotanyFixtureLayout.noRepeatTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
        var repeat = level.getBlockEntity(SmallBotanyFixtureLayout.repeatTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
        var self = level.getBlockEntity(SmallBotanyFixtureLayout.selfHarvestTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
        String harvestStatus = "A NO-REPEAT — "+harvestState(noRepeat)+"\nB REPEAT — "+harvestState(repeat)
                +"\nC SELF-HARVEST — "+(self == null ? "WAITING: tray unavailable" : switch (self.autoHarvestOutcome()) {
                    case SUCCESS -> "CHANGED: fruit released by ordinary automatic harvest";
                    case FAILED -> "WRITE FAILED: ordinary automatic harvest could not release fruit";
                    case NONE -> "WAITING: ordinary automatic harvest not yet reported";
                });
        var sampling = level.getBlockEntity(SmallBotanyFixtureLayout.samplingTray(origin)) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
        var samplingPlant = sampling == null ? null : sampling.plantState();
        String samplingStatus = "V SAMPLE — " + (samplingPlant == null || samplingPlant.seed() == null ? "WAITING: tray/plant unavailable"
                : "PRESET: resolved wheat, CanScream=" + samplingPlant.effectiveProfile().scream().enabled()
                + "; raw growth stage=" + com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator.rawGrowthStage(samplingPlant, samplingPlant.effectiveProfile())
                + "; alive=" + !samplingPlant.dead() + ", ready=" + samplingPlant.ready());
        BlockPos wing=SmallBotanyFixtureLayout.careOrigin(origin);
        String toleranceStatus=BotanyFixtureSigns.toleranceLabels(wing).stream().map(label -> {
            var t=level.getBlockEntity(label.trayPos()) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray?tray:null;
            var p=t==null?null:t.plantState();
            return label.title()+" — "+(p==null||p.seed()==null?"WAITING: tray/plant unavailable":"PRESET near-death; age="+(int)p.age()+", health="+(int)p.health()+", skip-aging="+(int)p.skipAging()+"; "+(p.dead()?"DEAD":"LIVING"));
        }).collect(java.util.stream.Collectors.joining("\n"));
        String air = AtmosphereService.INSTANCE.readAtmosphere(level,sample)
                .map(r -> r.status()+", pressure="+String.format(java.util.Locale.ROOT,"%.1f",r.mixture().pressureKpa(1.0))+" kPa, temperature="+String.format(java.util.Locale.ROOT,"%.1f",r.mixture().temperatureKelvin())+" K")
                .orElse("unknown (finite room ownership/discovery pending)");
        String stations = BotanyFixtureSigns.labels(origin).stream().map(BotanyFixtureSigns.Label::summary).collect(java.util.stream.Collectors.joining("\n"));
         return "BOTANY FIXTURE RESULTS\n"+stations+"\n"+harvestStatus+"\n"+samplingStatus+"\n"+toleranceStatus+"\n"+chemicalStatus+"\n"+statsStatus+"\n"+gasStatus+"\n"+showcaseStatus
                +"\nROOM / MACHINES — "+air+"\nPOWER DIAGNOSTICS (technical): APC="+(apc==null?"missing":"battery="+String.format(java.util.Locale.ROOT,"%.0f",apc.energyJoules())+" J, breaker="+(apc.breakerClosed()?"open":"closed"))+"; grinder="+(grinder==null?"unknown":grinder)+"; extractor="+(extractor==null?"unknown":extractor);
    }

    private static String gasStationStatus(ServerLevel level, BlockPos pos, String prefix) {
        var tray = level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity t ? t : null;
        return prefix + gasReportStatus(tray == null ? com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator.GasExchangeReport.none() : tray.gasExchangeReport());
    }

    private static String gasReportStatus(com.juicyslew.moonstation14.ms14.botany.PlantMetabolismEvaluator.GasExchangeReport report) {
        return !report.attempted() ? "WAITING: ordinary exchange cycle not reported"
                : !report.unmetRequiredGases().isEmpty() ? "MISSING GAS: " + report.unmetRequiredGases()
                : !report.failedExudationGases().isEmpty() ? "WRITE FAILED: exudation " + report.failedExudationGases()
                : "EXCHANGED: ordinary cycle report confirms gas exchange";
    }

    private static String harvestState(com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray) {
        if (tray == null || tray.plantState() == null) return "WAITING: tray unavailable";
        var plant = tray.plantState();
        if (plant.seed() == null) return "CHANGED: tray is empty after harvest";
        return switch (plant.effectiveProfile().lifecycle().harvest()) {
            case NO_REPEAT -> "PLANTED: harvest clears plant; currently "+(plant.ready()?"ready":"growing");
            case REPEAT -> "PLANTED: plant remains after harvest; currently "+(plant.ready()?"ready":"growing");
            case SELF_HARVEST -> "automatic mode; plant currently "+(plant.ready()?"ready":"growing");
        };
    }

    private static String build(ServerLevel level, BlockPos origin) {
        BlockPos wing=SmallBotanyFixtureLayout.careOrigin(origin);
        String layoutIssue = BotanyFixtureSigns.layoutProblem(origin);
        if (layoutIssue != null) return "Refused without changes: " + layoutIssue + ".";
        for(BlockPos p:SmallBotanyFixtureLayout.plannedFootprint(origin)) if(!emptyLoaded(level,p))
            return "Refused without changes: complete fixture/connector/sign/utility footprint is occupied, out of bounds, or unloaded at "+p+".";
        String plannedSigns=BotanyFixtureSigns.plannedLayoutProblem(origin);
        if(plannedSigns!=null) return "Refused without changes: invalid planned station sign layout: "+plannedSigns+".";
        for(BlockPos room:SmallBotanyFixtureLayout.roomOrigins(origin)) for(int x=0;x<WIDTH;x++) for(int y=0;y<HEIGHT;y++) for(int z=0;z<DEPTH;z++) {
            boolean wall=x==0||x==WIDTH-1||y==0||y==HEIGHT-1||z==0||z==DEPTH-1;
            level.setBlock(room.offset(x,y,z),wall?ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        }
        // Connector floor/roof/sides, then reopen both room doorways at the same clear lane.
        for(var cell:SmallBotanyFixtureLayout.connectorPlan(origin)) level.setBlock(cell.pos(),cell.opening()?Blocks.AIR.defaultBlockState():ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
        level.setBlock(SmallBotanyFixtureLayout.grinder(origin), ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.extractor(origin), ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
        for(var station:BotanyFixtureStations.all()) {
            BlockPos tray=SmallBotanyFixtureLayout.stationTray(origin,station.legacyId());
            for(int dx=-1;dx<=1;dx++) for(int dz=0;dz<=2;dz++) level.setBlock(tray.offset(dx,-1,dz),((dx+dz)&1)==0?Blocks.POLISHED_ANDESITE.defaultBlockState():Blocks.WHITE_CONCRETE.defaultBlockState(),3);
            level.setBlock(tray.offset(1,0,1),Blocks.TORCH.defaultBlockState(),3);
            boolean legacyDry=station.legacyId().equals("F")||station.legacyId().equals("G");
            level.setBlock(tray,(legacyDry?ModBlocks.EMPTY_HYDROPONICS_TRAY:ModBlocks.HYDROPONICS_TRAY).get().defaultBlockState(),3);
        }
        String signIssue = BotanyFixtureSigns.placementProblem(level, origin);
        if (signIssue != null) return "Partial build: signs refused before placement: " + signIssue + ".";
        BotanyFixtureSigns.place(level, origin);
        BotanyFixtureSigns.placeGuides(level, origin);
        for(var sign:BotanyFixtureSigns.careLabels(wing)) placeSign(level,sign);
        for(var sign:BotanyFixtureSigns.toleranceLabels(wing)) placeSign(level,sign);
        for (int z=-3; z<=3; z++) level.setBlock(origin.offset(8,1,z), Blocks.STONE.defaultBlockState(), 3);
        for (int z=-1; z<=1; z++) level.setBlock(origin.offset(6,1,z), Blocks.STONE.defaultBlockState(), 3);
        BlockPos apcPos=SmallBotanyFixtureLayout.apc(origin);
        if(!PowerDeviceFixturePlacement.placeApc(level,apcPos,net.minecraft.core.Direction.WEST)) return "Partial build: APC support/placement failed at "+apcPos+".";
        BlockPos source = origin.offset(7,1,-3), substation = origin.offset(7,1,-1);
        putPower(level, source, PowerDeviceKind.HV_SOURCE, net.minecraft.core.Direction.EAST);
        putPower(level, substation, PowerDeviceKind.HV_MV_SUBSTATION, net.minecraft.core.Direction.EAST);
        for (int z=-3; z<=-1; z++) if(!CableStorage.place(level,origin.offset(8,1,z),net.minecraft.core.Direction.WEST,CableTier.HV))
            return "Partial build: HV cable placement failed at "+origin.offset(8,1,z)+".";
        for (int z=-1; z<=1; z++) if(!CableStorage.place(level,origin.offset(6,1,z),net.minecraft.core.Direction.EAST,CableTier.MV))
            return "Partial build: MV cable placement failed at "+origin.offset(6,1,z)+".";
        for (int z=1; z<=3; z++) if(!CableStorage.place(level,origin.offset(8,1,z),net.minecraft.core.Direction.WEST,CableTier.APC))
            return "Partial build: APC cable placement failed at "+origin.offset(8,1,z)+".";
        // A vertical continuation from the existing APC-tier host row powers a real upper-wall lamp.
        for (int y=2; y<=3; y++) level.setBlock(origin.offset(8,y,2), Blocks.STONE.defaultBlockState(), 3);
        for (int y=2; y<=3; y++) if(!CableStorage.place(level,origin.offset(8,y,2),net.minecraft.core.Direction.WEST,CableTier.APC))
            return "Partial build: lamp APC cable placement failed at "+origin.offset(8,y,2)+".";
        putPower(level, SmallBotanyFixtureLayout.lamp(origin), PowerDeviceKind.LAMP, net.minecraft.core.Direction.EAST);
        // Dedicated edge-turn feed from the existing APC network to the care-wing lamp.
        for (var node : SmallBotanyFixtureLayout.careLightingCablePlan(origin))
            level.setBlock(node.pos(), Blocks.STONE.defaultBlockState(), 3);
        for (var node : SmallBotanyFixtureLayout.careLightingCablePlan(origin))
            if (!CableStorage.place(level, node.pos(), node.face(), CableTier.APC))
                return "Partial build: care-wing APC cable placement failed at " + node.pos() + ".";
        putPower(level, SmallBotanyFixtureLayout.careLamp(wing), PowerDeviceKind.LAMP, net.minecraft.core.Direction.NORTH);
        for(var node:SmallBotanyFixtureLayout.showcaseLightingCablePlan(origin)) level.setBlock(node.pos(),Blocks.STONE.defaultBlockState(),3);
        for(var node:SmallBotanyFixtureLayout.showcaseLightingCablePlan(origin)) if(!CableStorage.place(level,node.pos(),node.face(),CableTier.APC)) return "Partial build: showcase APC cable placement failed at "+node.pos()+".";
        putPower(level,SmallBotanyFixtureLayout.showcaseLamp(origin),PowerDeviceKind.LAMP,net.minecraft.core.Direction.NORTH);
        if(!(level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity battery)) return "Partial build: APC entity missing at "+apcPos+".";
        battery.setEnergyJoules(100_000);
        return "Built botany fixture [" + origin.getX() + "," + origin.getY() + "," + origin.getZ() + "]";
    }

    static List<BlockPos> mutationShowcaseTrays(BlockPos wing) {
        return List.of(SmallBotanyFixtureLayout.lowPressureTray(wing), SmallBotanyFixtureLayout.earlyFruitTray(wing),
                SmallBotanyFixtureLayout.ligneousTray(wing));
    }

    private static void putPower(ServerLevel level, BlockPos pos, PowerDeviceKind kind, net.minecraft.core.Direction facing) {
        var block = switch(kind) {
            case HV_SOURCE -> ModBlocks.HV_SOURCE.get();
            case HV_MV_SUBSTATION -> ModBlocks.HV_MV_SUBSTATION.get();
            case LAMP -> ModBlocks.POWER_LAMP.get();
            default -> throw new IllegalArgumentException("Unsupported fixture power device: " + kind);
        };
        level.setBlock(pos, block.defaultBlockState().setValue(PowerDeviceBlock.FACING, facing), 3);
    }

    private static void placeSign(ServerLevel level,BotanyFixtureSigns.Label label) {
        level.setBlock(label.pos(),Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION,label.rotation()),3);
        if(level.getBlockEntity(label.pos()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var text=sign.getFrontText();
            for(int i=0;i<label.lines().length;i++) text=text.setMessage(i,Component.literal(label.lines()[i]));
            sign.setText(text,true);
            var back=sign.getBackText();
            for(int i=0;i<label.lines().length;i++) back=back.setMessage(i,Component.literal(label.lines()[i]));
            sign.setText(back,false); sign.setChanged();
        }
    }

    private static boolean emptyLoaded(ServerLevel level,BlockPos p) {
        return p.getY()>=level.getMinBuildHeight()&&p.getY()<level.getMaxBuildHeight()&&level.getWorldBorder().isWithinBounds(p)
                &&level.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)!=null
                &&level.getBlockState(p).isAir()&&level.getBlockEntity(p)==null;
    }

    private static String enter(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level, BlockPos origin) {
        var body = authority.body();
        if (body.level() != level || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body)
            return "Enter refused: controlled body is not live in this dimension.";
        Vec3 target = SmallBotanyFixtureLayout.entry(origin);
        var box = body.getBoundingBox().move(target.subtract(body.position()));
        if (!level.hasChunkAt(BlockPos.containing(target)) || !level.noCollision(body, box)) return "Enter refused: interior destination is blocked or unloaded.";
        body.teleportTo(target.x, target.y, target.z);
        body.setDeltaMovement(Vec3.ZERO);
        return "Moved existing controlled body to botany fixture interior " + target + "; atmosphere/power initialization pending.";
    }
}
