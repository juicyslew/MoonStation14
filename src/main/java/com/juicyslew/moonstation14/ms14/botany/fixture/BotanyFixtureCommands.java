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

    private record Site(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos origin) { }
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
                .then(Commands.literal("status").executes(c -> runStatus(c.getSource())));
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
        Site site = new Site(source.getLevel().dimension(), origin);
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
            String scenarios = com.juicyslew.moonstation14.ms14.botany.BotanyFixtureScenarios.arrange(level, site.origin());
            if (!scenarios.startsWith("Arranged")) {
                if (task.ticks() + 1 < 20) { pending.put(playerId, new Pending(task.body(), task.epoch(), site, task.ticks() + 1, task.source())); continue; }
                pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: " + scenarios + " Partial room remains.")); continue;
            }
            String moved = enter(player, authority, level, site.origin());
            if (!moved.startsWith("Moved existing")) { pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed: " + moved + " Partial room remains.")); continue; }
            // Kit uses the existing body-authority grant path; it is deliberately the final, non-retried step.
            int granted = grantKit(player.createCommandSourceStack(), authority);
            if (granted == 0) { pending.remove(playerId); task.source().sendFailure(Component.literal("Botany fixture setup failed while granting supplies; partial room/body entry remains.")); continue; }
            pending.remove(playerId);
            task.source().sendSuccess(() -> Component.literal("Botany fixture ready: finite room air, authorized body entry, kit supplies granted, and five tray scenarios arranged."), false);
        }
        if (pending.isEmpty()) PENDING.remove(server);
    }

    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        PENDING.remove(event.getServer());
        SITES.remove(event.getServer());
    }

    private static boolean traysInitialized(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : List.of(SmallBotanyFixtureLayout.normalTray(origin, false), SmallBotanyFixtureLayout.normalTray(origin, true),
                SmallBotanyFixtureLayout.dryTray(origin, false), SmallBotanyFixtureLayout.dryTray(origin, true)))
            if (!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity tray)
                    || !tray.isLoadedValid() || tray.plantState() == null) return false;
        return true;
    }

    private static int grantKit(net.minecraft.commands.CommandSourceStack source, HandActorAuthority.Snapshot authority) {
        ServerPlayer actor = source.getPlayer();
        var body = authority.body();
        if (!ModSeedProfiles.serverReady()) { source.sendFailure(Component.literal("Seed prototypes are not ready; no kit supplied.")); return 0; }
        var catalog = ModSeedProfiles.serverCatalog();
        var packets = catalog.asMap().values().stream().filter(profile -> profile.lifecycle().maturation() > 0)
                .map(profile -> profile.packet()).distinct().sorted(java.util.Comparator.comparing(net.minecraft.resources.ResourceLocation::toString)).toList();
        if (packets.size() != 5) { source.sendFailure(Component.literal("Resolved seed catalog does not contain exactly five concrete packets; no kit supplied.")); return 0; }
        var itemRegistry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        for (var packet : packets) if (!itemRegistry.containsKey(packet) || itemRegistry.get(packet) == net.minecraft.world.item.Items.AIR) {
            source.sendFailure(Component.literal("Resolved seed packet item is not registered: " + packet + "; no kit supplied.")); return 0;
        }
        var water = ModReagents.createKey("water"); var ez = ModReagents.createKey("eznutrient");
        if (!ModReagents.catalog(source.getLevel()).contains(water.location()) || !ModReagents.catalog(source.getLevel()).contains(ez.location())) {
            source.sendFailure(Component.literal("Required water/EZ Nutrient reagent prototypes are not ready; no kit supplied.")); return 0;
        }
        java.util.List<ItemStack> stock = new ArrayList<>();
        for (var packet : packets) stock.add(new ItemStack(itemRegistry.get(packet)));
        stock.add(new ItemStack(ModItems.BOTANY_HOE.get()));
        stock.add(new ItemStack(ModItems.BOTANY_SPADE.get()));
        stock.add(new ItemStack(ModItems.BEAKER.get()));
        stock.add(beaker(Map.of(water, 2500L)));
        stock.add(beaker(Map.of(ez, 2500L)));
        for (ItemStack item : stock) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            if (!BodyGiveService.giveTrustedBody(body, item, () -> HandActorAuthority.revalidate(actor, authority))) {
                source.sendFailure(Component.literal("Kit grant stopped at " + itemId + ": body authority or item grant failed; previously granted supplies remain."));
                return 0;
            }
        }
        return 1;
    }

    private static ItemStack beaker(Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> cents) {
        ItemStack stack = new ItemStack(ModItems.BEAKER.get());
        stack.set(ModDataComponents.REAGENT.get(), ReagentComponent.fromCents(cents));
        return stack;
    }

    private static int runStatus(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer actor = source.getPlayer();
        UUID key = actor == null ? null : actor.getUUID();
        Site site = key == null ? null : SITES.computeIfAbsent(source.getServer(), ignored -> new java.util.HashMap<>()).get(key);
        String result = site == null || !site.dimension().equals(source.getLevel().dimension())
                ? "No botany fixture remembered in this dimension; run /ms14 botany_fixture first."
                : status(source.getLevel(), site.origin());
        final String message = result;
        source.sendSuccess(() -> Component.literal(message), false);
        return message.startsWith("Botany fixture") ? 1 : 0;
    }

    private static String initialize(ServerLevel level, BlockPos origin) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x=1;x<WIDTH-1;x++) for (int y=1;y<HEIGHT-1;y++) for (int z=1;z<DEPTH-1;z++) {
            BlockPos p=origin.offset(x,y,z);
            if (AtmosphereTopology.isPassable(level,p)) cells.add(p);
        }
        GasMixture air = new GasMixture(Map.of(GasType.OXYGEN,8.0,GasType.NITROGEN,30.0),293.15);
        if (!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,cells,air))
            return "Initialization not ready: room air is not fully discovered as a bounded finite enclosure. No gas written.";
        return "Initialized "+cells.size()+" enclosed finite owned room/headspace cells with 8 mol O2 + 30 mol N2 per cell at 293.15 K.";
    }

    private static String status(ServerLevel level, BlockPos origin) {
        BlockPos sample=SmallBotanyFixtureLayout.sample(origin);
        BlockPos grinderPos=SmallBotanyFixtureLayout.grinder(origin);
        var grinder=level.getBlockEntity(grinderPos) instanceof com.juicyslew.moonstation14.ms14.botany.ReagentGrinderBlockEntity machine ? PowerRuntime.processingMeter(level,machine) : null;
        BlockPos apcPos=SmallBotanyFixtureLayout.apc(origin);
        var apc=level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity device ? device : null;
        return "Botany fixture room air: "+AtmosphereService.INSTANCE.readAtmosphere(level,sample)
                .map(r -> r.status()+", pressure="+String.format(java.util.Locale.ROOT,"%.1f",r.mixture().pressureKpa(1.0))+" kPa, temperature="+String.format(java.util.Locale.ROOT,"%.1f",r.mixture().temperatureKelvin())+" K")
                .orElse("unknown (finite room ownership/discovery pending)")+"; APC="+(apc==null?"missing":"battery="+String.format(java.util.Locale.ROOT,"%.0f",apc.energyJoules())+" J, breaker="+(apc.breakerClosed()?"closed":"open"))+"; grinder meter="+(grinder==null?"unknown":grinder)+"; body inhalation is not asserted.";
    }

    private static String build(ServerLevel level, BlockPos origin) {
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            BlockPos p = origin.offset(x, y, z);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(p)
                    || level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false) == null)
                return "Refused without changes: complete room must be in bounds and loaded.";
            if (!level.getBlockState(p).isAir() || level.getBlockEntity(p) != null)
                return "Refused without changes: fixture footprint is occupied at " + p + ".";
        }
        // The real supply chain sits outside the room; preflight its entire bounded installation
        // area before writing either the room or any utility blocks.
        for (int x = 5; x <= 9; x++) for (int y = 0; y <= 3; y++) for (int z = -4; z <= -1; z++) {
            BlockPos p = origin.offset(x, y, z);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(p)
                    || level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false) == null
                    || !level.getBlockState(p).isAir() || level.getBlockEntity(p) != null)
                return "Refused without changes: external power utility footprint is occupied, out of bounds, or unloaded at " + p + ".";
        }
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) {
            boolean wall = x == 0 || x == WIDTH - 1 || y == 0 || y == HEIGHT - 1 || z == 0 || z == DEPTH - 1;
            level.setBlock(origin.offset(x, y, z), wall ? ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(SmallBotanyFixtureLayout.normalTray(origin, false), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.normalTray(origin, true), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.dryTray(origin, false), ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.dryTray(origin, true), ModBlocks.EMPTY_HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.untouchedTray(origin), ModBlocks.HYDROPONICS_TRAY.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.grinder(origin), ModBlocks.REAGENT_GRINDER.get().defaultBlockState(), 3);
        level.setBlock(SmallBotanyFixtureLayout.extractor(origin), ModBlocks.SEED_EXTRACTOR.get().defaultBlockState(), 3);
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
        if(!(level.getBlockEntity(apcPos) instanceof PowerDeviceBlockEntity battery)) return "Partial build: APC entity missing at "+apcPos+".";
        battery.setEnergyJoules(100_000);
        return "Built botany fixture [" + origin.getX() + "," + origin.getY() + "," + origin.getZ() + "]";
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
