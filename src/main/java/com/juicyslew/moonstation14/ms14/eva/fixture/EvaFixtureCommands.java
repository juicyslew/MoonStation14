package com.juicyslew.moonstation14.ms14.eva.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModEquipmentItems;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.fixture.FixtureSiteContext;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;

/** Bounded operator-built EVA test enclosure. Its gas writes are explicit finite-room setup only. */
public final class EvaFixtureCommands {
    private static final int W=EvaFixturePlan.WIDTH,H=EvaFixturePlan.HEIGHT,D=EvaFixturePlan.DEPTH;
    private static final Map<MinecraftServer, FixtureSiteContext> SITES=Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean stopListener;
    private EvaFixtureCommands() { }

    /** Removes only one terminal fixture-test/operator site; production server-stop cleanup remains authoritative. */
    public static void forgetFixtureSiteForTest(MinecraftServer server, UUID operator, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        FixtureSiteContext sites=SITES.get(server);if(sites!=null)sites.forget(operator,dimension);
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        if(!stopListener){NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->{var s=SITES.remove(e.getServer());if(s!=null)s.clear();});stopListener=true;}
        var root=Commands.literal("eva_fixture");
        root.then(Commands.literal("build").executes(c->run(c.getSource(),"build",null)));
        for(String op:List.of("status","reset")) root.then(Commands.literal(op).executes(c->run(c.getSource(),op,null)));
        root.then(Commands.literal("scenario").then(Commands.argument("scenario",com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((c,b)->{for(String s:List.of("normal","vacuum","cold","hot","contaminated"))b.suggest(s);return b.buildFuture();})
                .executes(c->run(c.getSource(),"scenario",com.mojang.brigadier.arguments.StringArgumentType.getString(c,"scenario")))));
        event.getDispatcher().register(Commands.literal("ms14").requires(s->s.hasPermission(2)).then(root));
    }

    private static int run(net.minecraft.commands.CommandSourceStack source,String op,String argument) {
        var player=source.getPlayer();var authority=player==null?null:HandActorAuthority.resolve(player).orElse(null);
        UUID operator=source.getEntity()==null?new UUID(0,0):source.getEntity().getUUID();
        var sites=SITES.computeIfAbsent(source.getServer(),ignored->new FixtureSiteContext());
        BlockPos site;
        if(op.equals("build")) {
            if(authority==null||authority.body().level()!=source.getLevel()){source.sendFailure(Component.literal("Build requires your authorized controlled body in this dimension."));return 0;}
            site=authority.body().blockPosition().offset(3,0,-D/2);
            String failure=preflight(source.getLevel(),site);
            if(failure!=null){source.sendFailure(Component.literal("EVA fixture refused without changes: "+failure));return 0;}
            var level=source.getLevel();
            for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){
                boolean shell=x==0||x==W-1||y==0||y==H-1||z==0||z==D-1;
                BlockPos pos=site.offset(x,y,z);
                var state=!shell?Blocks.AIR.defaultBlockState():EvaFixturePlan.isLightingPosition(site,pos)
                        ?Blocks.SEA_LANTERN.defaultBlockState():ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState();
                level.setBlock(pos,state,3);
            }
            if(!HandActorAuthority.revalidate(player,authority)||authority.body().level()!=level||authority.body().isRemoved()||!authority.body().isAlive()) {
                source.sendFailure(Component.literal("EVA room built, but your controlled body is no longer available; no body moved or kit granted."));return 0;
            }
            String equipped=prepareEVA(source,authority);
            if(equipped!=null){source.sendFailure(Component.literal("EVA room shell built, but ready setup failed safely: "+equipped));return 0;}
            var body=authority.body();
            var worn=com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body);
            var character=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body).orElse(null);
            var policy=character==null?null:character.component(com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent.class).orElse(null);
            boolean protectedGear=policy!=null&&com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection.pressure(worn,policy).isPresent();
            var connected=com.juicyslew.moonstation14.ms14.lung.InternalsSystem.status(body);
            var oxygen=com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
            if(!protectedGear||!connected.connected()||!connected.working()||oxygen==null||oxygen.contents().mixture().totalMoles()<=0){source.sendFailure(Component.literal("EVA ready checks failed before vacuum/entry; body remains outside: pressure protection or working oxygen unavailable."));return 0;}
            String[] rejection={"unknown trusted setup rejection"};
            if(!AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,cells(site),GasMixture.vacuum(),reason->rejection[0]=reason)) {
                source.sendFailure(Component.literal("EVA gear and oxygen are ready, but finite vacuum setup failed: "+rejection[0]+". Body remains outside."));return 0;
            }
            String entered=enter(level,site,authority);
            if(entered.startsWith("Refused")) {source.sendFailure(Component.literal("EVA room built, but automatic entry failed: "+entered));return 0;}
            sites.buildResult(operator,level.dimension(),site,true);
            source.sendSuccess(()->Component.literal("Vacuum test ready. EVA equipped; oxygen connected. Toggle Internals off to test breathing. Reset restores room air only."),false);return 1;
        }
        site=sites.find(operator,source.getLevel().dimension());
        if(site==null){source.sendFailure(Component.literal("No EVA fixture remembered in this dimension; run build first."));return 0;}
        String result=switch(op){case "scenario"->scenario(source.getLevel(),site,argument);case "reset"->scenario(source.getLevel(),site,"normal");case "status"->status(source.getLevel(),site,authority==null?null:authority.body());default->"Unknown operation.";};
        if(result.startsWith("Refused")||result.startsWith("NOT READY")||result.startsWith("Failed")){source.sendFailure(Component.literal(result));return 0;}
        source.sendSuccess(()->Component.literal(result),false);return 1;
    }

    private static String preflight(ServerLevel l,BlockPos o){
        for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){BlockPos p=o.offset(x,y,z);
            if(p.getY()<l.getMinBuildHeight()||p.getY()>=l.getMaxBuildHeight()||!l.getWorldBorder().isWithinBounds(p)||l.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return "complete footprint must be in bounds and already loaded";
            if(!l.getBlockState(p).isAir()||!l.getFluidState(p).isEmpty()||l.getBlockEntity(p)!=null)return "footprint must be clear; blocked "+p.toShortString();
        }
        var box=new net.minecraft.world.phys.AABB(o.getX(),o.getY(),o.getZ(),o.getX()+W,o.getY()+H,o.getZ()+D);
        return l.getEntities((net.minecraft.world.entity.Entity)null,box,net.minecraft.world.entity.Entity::isAlive).isEmpty()?null:"footprint contains a living entity";
    }
    private static List<BlockPos> cells(BlockPos o){return EvaFixturePlan.interior(o);}
    private static boolean enclosure(ServerLevel l,BlockPos o){for(int x=0;x<W;x++)for(int y=0;y<H;y++)for(int z=0;z<D;z++){BlockPos p=o.offset(x,y,z);if(!l.getWorldBorder().isWithinBounds(p)||l.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return false;boolean shell=x==0||x==W-1||y==0||y==H-1||z==0||z==D-1;boolean light=EvaFixturePlan.isLightingPosition(o,p);if(shell?(light?!l.getBlockState(p).is(Blocks.SEA_LANTERN):!l.getBlockState(p).is(ModBlocks.STEEL_WALL_BLOCK.get())):!l.getBlockState(p).isAir())return false;}return true;}
    private static String initialize(ServerLevel l,BlockPos o){if(!enclosure(l,o))return "Refused: remembered sealed enclosure mismatch or unloaded bounds.";var c=cells(o);for(var p:c)if(AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(l,p).isEmpty())return "NOT READY: finite owned gas cells are not ready; wait and retry, no gas written.";return AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(l,c,GasMixture.breathableAir())?"Initialized "+c.size()+" owned finite room cells with breathable air (101.325 kPa target, 293.15 K); no tank/body state changed.":"Failed: finite enclosure gas ownership/setup admission rejected.";}
    private static String scenario(ServerLevel l,BlockPos o,String name){if(!List.of("normal","vacuum","cold","hot","contaminated").contains(name))return "Refused: scenario must be normal, vacuum, cold, hot, or contaminated.";if(!enclosure(l,o))return "Refused: remembered sealed enclosure mismatch or unloaded bounds.";var c=cells(o);var mixtures=new ArrayList<GasMixture>();for(var p:c){var e=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(l,p).orElse(null);if(e==null||e.snapshot()==null)return "NOT READY: finite owned gas cells are not ready; no gas written.";mixtures.add(EvaFixturePlan.mixture(name,e.snapshot().volumeCubicMeters()));}
        for(int i=0;i<c.size();i++)if(!AtmosphereService.INSTANCE.initializeFiniteMixtureForTrustedSetup(l,c.get(i),mixtures.get(i)))return "Failed: trusted finite gas write failed at "+c.get(i).toShortString()+"; inspect enclosure.";
        return "Applied finite "+name+" gas to "+c.size()+" owned cells; cold/hot are setup temperatures only, no damage forced.";}
    private static String status(ServerLevel l,BlockPos o,net.minecraft.world.entity.LivingEntity body){
        if(!enclosure(l,o))return "NOT READY: sealed fixture bounds/blocks do not validate or are unloaded.";
        var sample=AtmosphereService.INSTANCE.readAtmosphere(l,o.offset(3,2,3));
        String room=sample.map(r->r.status()+", "+String.format(Locale.ROOT,"%.2f kPa, %.2f K",r.mixture().pressureKpa(r.mixture().volumeCubicMeters()),r.mixture().temperatureKelvin())).orElse("unknown sample/ownership");
        if(body==null||body.isRemoved()||!body.isAlive()||body.level()!=l)return "EVA room="+room+", interiorCells="+cells(o).size()+", controlled body=unknown/unavailable; gear, internals, temperature and saturation unknown.";
        var worn=com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.entries(body);
        var head=worn.stream().filter(e->e.slotId().equals("head")).findFirst().orElse(null);
        var suit=worn.stream().filter(e->e.slotId().equals("outerClothing")).findFirst().orElse(null);
        String gear="head="+describeGear(head)+", suit="+describeGear(suit);
        var character=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(body).orElse(null);
        var policy=character==null?null:character.component(com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent.class).orElse(null);
        var protection=policy==null?Optional.<com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.PressureProtection>empty():com.juicyslew.moonstation14.ms14.equipment.EquipmentProtectionProjection.pressure(worn,policy);
        gear+="; pressureCoverage="+(policy==null?"unknown (no live barotrauma profile)":protection.map(p->"protected; required="+policy.protectionSlots()+", multipliers high="+p.highPressureMultiplier()+" low="+p.lowPressureMultiplier()).orElse("incomplete"));
        var internals=com.juicyslew.moonstation14.ms14.lung.InternalsSystem.status(body);
        var source=com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).orElse(null);
        String internal="connected="+internals.connected()+", working="+internals.working();
        if(source==null)internal+=internals.connected()?", source=unavailable/stale, pressure=unknown, outlet=unknown, low/empty=unknown":", source=none, internal pressure=0.00 kPa, outlet=none, low/empty=not applicable";
        else {var contents=source.contents();var profile=source.profile();double ip=contents.mixture().pressureKpa(profile.volumeCubicMeters());internal+=", source="+source.stack().getHoverName().getString()+" ("+source.slot()+"), internal="+String.format(Locale.ROOT,"%.2f kPa",ip)+", outlet="+String.format(Locale.ROOT,"%.2f kPa",profile.outletPressureKpa())+", low="+(ip<=profile.lowPressureThresholdKpa())+", empty="+(contents.mixture().totalMoles()<=0);}
        var temp=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.BODY_TEMPERATURE.get());
        var respiration=com.juicyslew.moonstation14.ms14.organ.BodySystem.current(body).map(s->s.respiration()).orElse(null);
        return "EVA room="+room+", interiorCells="+cells(o).size()+", body="+body.getName().getString()+", "+gear+", internals="+internal+", bodyTemp="+(temp==null?"unknown":String.format(Locale.ROOT,"%.2f K",temp.kelvin()))+", saturation="+(respiration==null?"unknown":String.format(Locale.ROOT,"%.2f",respiration.saturation()));
    }
    private static String describeGear(com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment.Entry e){return e==null?"empty":e.stack().getHoverName().getString()+" ("+(e.profile().pressureProtection().isPresent()?"pressure-rated":"no pressure protection profile")+")";}
    private static String enter(ServerLevel l,BlockPos o,HandActorAuthority.Snapshot authority){if(authority==null)return "Refused: enter requires currently authorized controlled body.";var b=authority.body();BlockPos target=o.offset(3,1,3);var box=b.getBoundingBox().move(target.getX()+.5-b.getX(),target.getY()-b.getY(),target.getZ()+.5-b.getZ());if(!enclosure(l,o)||b.level()!=l||b.isRemoved()||!b.isAlive()||!l.hasChunkAt(target)||!l.noCollision(b,box))return "Refused: exact enclosure or clear loaded interior/body validation failed.";b.teleportTo(target.getX()+.5,target.getY(),target.getZ()+.5);b.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return "Moved existing authorized controlled body into sealed fixture interior; no body/camera binding changed.";}
    private static String kit(net.minecraft.commands.CommandSourceStack s,HandActorAuthority.Snapshot a,String piece){if(a==null)return "Refused: kit requires authorized controlled body.";var tanks=ModGasTanks.catalog(s.getLevel());var entries=new LinkedHashMap<String,ItemStack>();entries.put("helmet",new ItemStack(ModEquipmentItems.EVA_HELMET.get()));entries.put("suit",new ItemStack(ModEquipmentItems.EVA_SUIT.get()));entries.put("mask",new ItemStack(ModEquipmentItems.BREATHING_MASK.get()));entries.put("tank",GasTankItem.createStack(tanks,ModGasTanks.OXYGEN_FILLED_ID));entries.put("low_tank",lowTank(tanks));entries.put("emergency_tank",GasTankItem.createStack(tanks,ModGasTanks.EMERGENCY_OXYGEN_FILLED_ID));if(piece.equals("all")){for(var e:entries.entrySet())if(!give(s,a,e.getValue()))return "Failed: kit stopped at "+e.getKey()+"; earlier items may already have been granted.";return "Granted EVA kit items to controlled body hands/world; equip/connect them through normal gameplay.";}var stack=entries.get(piece);return stack==null?"Refused: choose helmet, suit, mask, tank, low_tank, or emergency_tank.":give(s,a,stack)?"Granted "+piece+" via controlled-body give; equip/connect normally.":"Failed: controlled-body give rejected; no forced equipment or tank connection.";}
    private static ItemStack lowTank(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData> catalog){var profile=catalog.get(ModGasTanks.OXYGEN_ID);if(profile==null)throw new IllegalStateException("Missing oxygen tank prototype");var stack=GasTankItem.createStack(catalog,ModGasTanks.OXYGEN_ID);stack.set(com.juicyslew.moonstation14.component.ModDataComponents.GAS_TANK_CONTENTS.get(),EvaFixturePlan.lowOxygenContents(profile));return stack;}
    private static String prepareEVA(net.minecraft.commands.CommandSourceStack source,HandActorAuthority.Snapshot authority){
        var player=source.getPlayer();var body=authority.body();
        for(var item:List.of(ModEquipmentItems.EVA_SUIT.get(),ModEquipmentItems.EVA_HELMET.get(),ModEquipmentItems.BREATHING_MASK.get())){
            if(!HandActorAuthority.revalidate(player,authority))return "controlled-body authority changed";
            if(!give(source,authority,new ItemStack(item)))return "could not safely grant required gear";
            var hands=com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElse(null);if(hands==null)return "body hands unavailable";
            String hand=hands.handIds().stream().filter(id->hands.stackCopy(id).filter(s->s.is(item)).isPresent()).findFirst().orElse(null);
            if(hand==null)return "required gear has no controlled-body hand";
            var result=com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquip(player,hand,hands.token(hand).orElse(""),hands.revision());
            if(result!=com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS)return "required gear could not be safely equipped ("+result+")";
        }
        var tanks=ModGasTanks.catalog(source.getLevel());var tank=GasTankItem.createStack(tanks,ModGasTanks.OXYGEN_FILLED_ID);
        if(!give(source,authority,tank))return "could not grant finite filled oxygen tank";
        var hands=com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElse(null);if(hands==null)return "body hands unavailable for oxygen tank";
        String token=null;for(String hand:hands.handIds())if(hands.stackCopy(hand).filter(s->GasTankItem.contents(s,tanks).map(c->c.prototypeId().equals(ModGasTanks.OXYGEN_FILLED_ID)).orElse(false)).isPresent()){token=hands.token(hand).orElse(null);break;}
        if(token==null)return "filled oxygen tank is not owned in a live hand";
        final String tankToken=token;
        if(!com.juicyslew.moonstation14.ms14.lung.InternalsSystem.isConnected(body)||com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).map(s->s.token().equals(tankToken)).orElse(false)){
            if(!com.juicyslew.moonstation14.ms14.lung.InternalsSystem.toggle(body,tankToken))return "oxygen source could not be connected";
        }
        return com.juicyslew.moonstation14.ms14.lung.InternalsSystem.selectedTank(body).filter(s->s.token().equals(tankToken)&&s.contents().mixture().totalMoles()>0).isPresent()?null:"selected oxygen source did not validate";
    }
    private static boolean give(net.minecraft.commands.CommandSourceStack s,HandActorAuthority.Snapshot a,ItemStack stack){var p=s.getPlayer();return p!=null&&BodyGiveService.giveTrustedBody(a.body(),stack,()->HandActorAuthority.revalidate(p,a));}
}
