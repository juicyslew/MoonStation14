package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.botany.ModSeedProfiles;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Supplies staged as ordinary, collision-checked world owners beside their relevant bays. */
public final class BotanyFixtureSupplies {
    public enum Kind { SEED, TOOL, EMPTY_BEAKER, REAGENT }
    public record Descriptor(String label,String stationId,Kind kind,String value,long cents) { }
    private record Supply(Descriptor descriptor, ItemStack stack) { }
    /** Pure, inspectable station-to-position plan used by publication and fixture tests. */
    public record PlannedSupply(String label, String stationId, int slot, BlockPos position) { }
    public static List<Descriptor> descriptors() {
        var out=new ArrayList<Descriptor>();
        for(String seed:List.of("wheat","tomato","potato","poppy","aloe")) out.add(new Descriptor(seed,switch(seed){case "wheat"->"A";case "tomato"->"B";case "potato"->"C";default->"H";},Kind.SEED,seed,0));
        for(String tool:List.of("hoe","spade","wirecutter","knife")) out.add(new Descriptor(tool,switch(tool){case "hoe","spade"->"F";case "wirecutter"->"V";default->"I";},Kind.TOOL,tool,0));
        out.add(new Descriptor("beaker","H",Kind.EMPTY_BEAKER,"",0));
        out.add(new Descriptor("water","G",Kind.REAGENT,"water",2500)); out.add(new Descriptor("eznutrient","G",Kind.REAGENT,"eznutrient",2500));
        for(var dose:BotanyFixtureSigns.allFixtureDoses()) out.add(new Descriptor(dose.displayStationName()+" "+dose.cents()/100+"u "+dose.reagent(),dose.station().split(" ")[0],Kind.REAGENT,dose.reagent(),dose.cents()));
        for(int i=1;i<=4;i++) out.add(new Descriptor("MUTATION RESERVE "+i+" 5u unstablemutagen","I",Kind.REAGENT,"unstablemutagen",500));
        return List.copyOf(out);
    }
    public static List<PlannedSupply> plan(BlockPos origin, List<Descriptor> descriptors) {
        var indices=new java.util.HashMap<String,Integer>(); var out=new ArrayList<PlannedSupply>();
        for(var descriptor:descriptors) { String id=descriptor.stationId(); int slot=indices.merge(id,1,Integer::sum)-1;
            out.add(new PlannedSupply(descriptor.label(),id,slot,SmallBotanyFixtureLayout.supplyPositionNear(SmallBotanyFixtureLayout.stationTray(origin,id),slot))); }
        return List.copyOf(out);
    }
    private BotanyFixtureSupplies() { }

    public static boolean publish(ServerPlayer actor, HandActorAuthority.Snapshot authority, ServerLevel level, BlockPos origin) {
        if (actor == null || authority == null || level == null || origin == null || actor != authority.actor()
                || actor.level() != level || !level.getServer().isSameThread() || !HandActorAuthority.revalidate(actor, authority)) return false;
        if (!ModSeedProfiles.serverReady()) return false;
        var catalog = ModSeedProfiles.serverCatalog();
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var supplies = new ArrayList<Supply>();
        var reagentCatalog=ModReagents.catalog(level);
        try {
            for(var descriptor:descriptors()) {
                ItemStack stack;
                if(descriptor.kind()==Kind.SEED) { var profile=catalog.get(ModSeedProfiles.seedId(descriptor.value())); if(profile==null||!items.containsKey(profile.packet())||items.get(profile.packet())==net.minecraft.world.item.Items.AIR)return false; stack=new ItemStack(items.get(profile.packet())); }
                else if(descriptor.kind()==Kind.TOOL) stack=new ItemStack(switch(descriptor.value()){case "hoe"->ModItems.BOTANY_HOE.get();case "spade"->ModItems.BOTANY_SPADE.get();case "wirecutter"->ModItems.WIRECUTTER.get();default->ModItems.KITCHEN_KNIFE.get();});
                else if(descriptor.kind()==Kind.EMPTY_BEAKER) stack=new ItemStack(ModItems.BEAKER.get());
                 else {var reagent=ModReagents.createKey(descriptor.value()); if(!reagentCatalog.contains(reagent.location()))return false; stack=beaker(Map.of(reagent,descriptor.cents())); }
                supplies.add(new Supply(descriptor,stack));
            }
        } catch (RuntimeException unresolved) { return false; }
        var positions=new java.util.HashSet<BlockPos>();
        List<PlannedSupply> plan;
        try { plan=plan(origin,supplies.stream().map(Supply::descriptor).toList()); }
        catch(IllegalArgumentException invalid){ return false; }
        for(var entry:plan){
            BlockPos pos=entry.position();
            if(!insideAnyRoom(origin,pos) || !positions.add(pos) || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())
                    || !level.getBlockState(pos.below()).isSolid()) return false;
        }
        for (int i=0;i<supplies.size();i++) {
            if (!HandActorAuthority.revalidate(actor,authority)) { actor.sendSystemMessage(Component.literal("Fixture supplies stopped; earlier supplies remain placed.")); return false; }
            var s=supplies.get(i);
            BlockPos pos=plan.get(i).position();
            var result=FreshWorldOutputBatch.publishSystemHarvest(level,pos,List.of(s.stack()),()->HandActorAuthority.revalidate(actor,authority),()->{});
            if(result!=BodyHandItemTransfer.Result.SUCCESS){actor.sendSystemMessage(Component.literal("Fixture supplies stopped at "+s.stack().getHoverName().getString()+"; earlier supplies remain placed."));return false;}
        }
        return true;
    }
    private static boolean insideAnyRoom(BlockPos origin,BlockPos p){for(BlockPos room:SmallBotanyFixtureLayout.roomOrigins(origin))if(p.getX()>=room.getX()+1&&p.getX()<=room.getX()+SmallBotanyFixtureLayout.WIDTH-2&&p.getY()==room.getY()+1&&p.getZ()>=room.getZ()+1&&p.getZ()<=room.getZ()+SmallBotanyFixtureLayout.DEPTH-2)return true;return false;}
    private static ItemStack beaker(Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>,Long> content){ItemStack stack=new ItemStack(ModItems.BEAKER.get());stack.set(ModDataComponents.REAGENT.get(),ReagentComponent.fromCents(content));return stack;}
}
