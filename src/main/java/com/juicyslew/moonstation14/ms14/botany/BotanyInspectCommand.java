package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Owner-authorized development diagnostic; deliberately not an ordinary examination path. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class BotanyInspectCommand {
    private static final Map<MinecraftServer, Map<UUID, Snapshot>> BASELINES = new WeakHashMap<>();
    private BotanyInspectCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(s -> s.hasPermission(2))
                .then(Commands.literal("botany_inspect")
                        .executes(c -> execute(c.getSource(), "capture"))
                        .then(Commands.literal("capture").executes(c -> execute(c.getSource(), "capture")))
                        .then(Commands.literal("compare").executes(c -> execute(c.getSource(), "compare")))
                        .then(Commands.literal("clear").executes(c -> execute(c.getSource(), "clear")))));
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { BASELINES.remove(e.getServer()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            var map=BASELINES.get(p.getServer()); if(map!=null) map.remove(p.getUUID());
        }
    }
    private static int execute(net.minecraft.commands.CommandSourceStack source, String action) {
        if (!source.hasPermission(2) || !(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.getServer()==null || !player.getServer().isSameThread()
                || player.getServer().getPlayerList().getPlayer(player.getUUID())!=player) return 0;
        var authority=BodyActionAuthority.resolve(player).orElse(null);
        if(authority==null || !BodyActionAuthority.revalidate(player,authority)) { source.sendFailure(Component.literal("Requires an online player with a currently authorized body.")); return 0; }
        var bases=BASELINES.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        if(action.equals("clear")) { bases.remove(player.getUUID()); source.sendSuccess(()->Component.literal("Botany inspection baseline cleared."),false); return 1; }
        var body=authority.body();
        if (!(body.level() instanceof net.minecraft.server.level.ServerLevel level) || level.getServer()!=player.getServer()) { source.sendFailure(Component.literal("Authorized body has no valid server level.")); return 0; }
        var target=BodyBlockUseTargetProbe.probeSelected(level,body);
        if(target==null || !BodyBlockUseTargetProbe.withinReach(level,body,target.pos())) { source.sendFailure(Component.literal("No bounded loaded block target in the current body view.")); return 0; }
        if(!(level.getBlockEntity(target.pos()) instanceof HydroponicsTrayBlockEntity tray)
                || !tray.isLoadedValid() || tray.plantState()==null || tray.plantState().seed()==null || tray.plantState().genetics()==null) {
            source.sendFailure(Component.literal("Target must be an initialized, nonempty hydroponics tray.")); return 0;
        }
        if (!BodyActionAuthority.revalidate(player, authority)) { source.sendFailure(Component.literal("Body authority changed; retry inspection.")); return 0; }
        Snapshot current=Snapshot.of(level.dimension().location().toString(),target.pos(),level.getGameTime(),tray);
        if(action.equals("capture")) { bases.put(player.getUUID(),current); current.lines("CAPTURED BASELINE",List.of()).forEach(line->source.sendSuccess(()->Component.literal(line),false)); return 1; }
        Snapshot prior=bases.get(player.getUUID());
        if(prior==null) { source.sendFailure(Component.literal("No baseline; run /ms14 botany_inspect capture.")); return 0; }
        if(!prior.sameTarget(current)) { source.sendFailure(Component.literal("Baseline target differs; capture this tray first.")); return 0; }
        current.lines("CURRENT SAMPLE; CHANGES SINCE PRIOR SAMPLE",current.differences(prior)).forEach(line->source.sendSuccess(()->Component.literal(line),false));
        return 1;
    }
    record Snapshot(String dimension, BlockPos pos, long tick, long revision, Map<String,String> fields) {
        Snapshot { pos=pos.immutable(); fields=Collections.unmodifiableMap(new TreeMap<>(fields)); }
        boolean sameTarget(Snapshot other) { return dimension.equals(other.dimension) && pos.equals(other.pos); }
        static Snapshot of(String dim, BlockPos pos, long tick, HydroponicsTrayBlockEntity tray) {
            var outcomes=new TreeMap<String,String>();
            outcomes.put("tray.mutationOutcome",tray.mutationResult().toString()); outcomes.put("tray.gasExchangeOutcome",tray.gasExchangeReport().toString());
            outcomes.put("tray.unsupportedMetabolism",tray.unsupportedMetabolism().toString()); outcomes.put("tray.warningBits",Integer.toString(tray.warningBits()));
            return capture(dim,pos,tick,tray.plantState(),outcomes);
        }
        static Snapshot capture(String dim, BlockPos pos, long tick, PlantInstance p, Map<String,String> outcomes) {
            GeneticValue genetics=p.genetics(); SeedProfile s=genetics.profile();
             var f=new TreeMap<String,String>();
             f.put("species",genetics.species().toString()); f.put("source identity",s.sourceName());
             f.put("packet identity",s.packet().toString()); f.put("product identity",s.product().toString());
             f.put("visual identity",s.visualKey());
             var produce = ModProduceProfiles.serverCatalog();
             var produceProfile = produce == null ? null : produce.get(s.product());
             f.put("product consumer", produceProfile == null ? "unsupported: no registered produce profile" : "registered produce profile");
             f.put("harvest item consumer", net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(s.product())
                     ? "registered item" : "unsupported: item not registered");
            f.put("age",Float.toString(p.age())); f.put("health",Float.toString(p.health())); f.put("water",Float.toString(p.water())); f.put("nutrition",Float.toString(p.nutrition()));
            f.put("weeds",Float.toString(p.weeds())); f.put("toxins",Float.toString(p.toxins())); f.put("pests",Float.toString(p.pests())); f.put("ready",Boolean.toString(p.ready())); f.put("dead",Boolean.toString(p.dead())); f.put("sampled",Boolean.toString(p.sampled()));
            f.put("mutationLevel",Float.toString(p.mutationLevel())); f.put("mutationModifier",Float.toString(p.mutationModifier()));
            f.put("reference.unique",Boolean.toString(p.seedReference().unique()));
            var b=s.biology(); f.put("biology.endurance",Float.toString(b.endurance())); f.put("biology.nutrientConsumption",Float.toString(b.nutrientConsumption())); f.put("biology.waterConsumption",Float.toString(b.waterConsumption())); f.put("biology.idealHeat",Float.toString(b.idealHeat())); f.put("biology.heatTolerance",Float.toString(b.heatTolerance())); f.put("biology.lowPressure",Float.toString(b.lowPressure())); f.put("biology.highPressure",Float.toString(b.highPressure())); f.put("biology.weedTolerance",Float.toString(b.weedTolerance())); f.put("biology.weedHighLevel",Float.toString(b.weedHighLevel())); f.put("biology.toxinTolerance",Float.toString(b.toxinTolerance())); f.put("biology.pestTolerance",Float.toString(b.pestTolerance()));
            var l=s.lifecycle(); f.put("lifecycle.growthStages",Integer.toString(l.growthStages())); f.put("lifecycle.lifespan",Float.toString(l.lifespan())); f.put("lifecycle.maturation",Float.toString(l.maturation())); f.put("lifecycle.production",Float.toString(l.production())); f.put("lifecycle.yield",Integer.toString(l.yield())); f.put("lifecycle.potency",Float.toString(l.potency())); f.put("lifecycle.harvest",l.harvest().name()); f.put("lifecycle.viable",Boolean.toString(l.viable())); f.put("lifecycle.seedless",Boolean.toString(l.seedless())); f.put("lifecycle.ligneous",Boolean.toString(l.ligneous()));
            s.chemicals().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> { var c=e.getValue(); f.put("chemical."+e.getKey()+".min",Float.toString(c.min())); f.put("chemical."+e.getKey()+".max",Float.toString(c.max())); f.put("chemical."+e.getKey()+".potencyDivisor",Float.toString(c.potencyDivisor())); f.put("chemical."+e.getKey()+".inherent",Boolean.toString(c.inherent())); });
            s.consumeGasses().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().id())).forEach(e->f.put("consumeGas."+e.getKey().id(),e.getValue().toString())); s.exudeGasses().entrySet().stream().sorted(Comparator.comparing(e->e.getKey().id())).forEach(e->f.put("exudeGas."+e.getKey().id(),e.getValue().toString()));
             var targets=s.mutationTargets().stream().map(Object::toString).sorted().toList(); for(int i=0;i<targets.size();i++) f.put("mutationTarget."+i,targets.get(i));
             var mutations=genetics.mutations();
             f.put("mutations.count", Integer.toString(mutations.size()));
             for(int i=0;i<mutations.size();i++) {
                 var mutation=mutations.get(i); String prefix="mutation."+i+".";
                 f.put(prefix+"name",mutation.name());
                 f.put(prefix+"appliesToPlant",Boolean.toString(mutation.appliesToPlant()));
                 f.put(prefix+"appliesToProduce",Boolean.toString(mutation.appliesToProduce()));
                 f.put(prefix+"persists",Boolean.toString(mutation.persists()));
                 var slippery=mutation.slippery();
                 var trigger=slippery.stepTrigger();
                 f.put(prefix+"slippery.stepTrigger.active",Boolean.toString(trigger.active()));
                 f.put(prefix+"slippery.stepTrigger.intersectionRatio",Double.toString(trigger.intersectionRatio()));
                 f.put(prefix+"slippery.stepTrigger.requiredSpeed",Double.toString(trigger.requiredSpeed()));
                 f.put(prefix+"slippery.stepTrigger.ignoreWeightless",Boolean.toString(trigger.ignoreWeightless()));
                 f.put(prefix+"slippery.response",slippery.response().toString());
             }
            f.put("scream.enabled",Boolean.toString(s.scream().enabled())); f.put("scream.sound",s.scream().sound().toString()); f.put("scream.volumeDb",Float.toString(s.scream().volumeDb()));
            f.put("lifecycle harvest capability",s.lifecycle().harvest().name());
            f.put("seed viable capability",Boolean.toString(s.lifecycle().viable()));
            f.put("seedless capability",Boolean.toString(s.lifecycle().seedless()));
            f.put("ligneous capability",Boolean.toString(s.lifecycle().ligneous()));
            outcomes.forEach((k,v)->f.put("outcome."+k,v));
            return new Snapshot(dim,pos,tick,p.revision(),f);
        }
        List<String> differences(Snapshot old) { var out=new ArrayList<String>(); var keys=new TreeSet<String>(); keys.addAll(fields.keySet());keys.addAll(old.fields.keySet()); for(String k:keys) if(!Objects.equals(fields.get(k),old.fields.get(k)))out.add(k+": "+old.fields.get(k)+" → "+fields.get(k)); return out.isEmpty()?List.of("No named fields changed."):List.copyOf(out); }
         List<String> lines(String heading,List<String> diff) { var out=new ArrayList<String>(); out.add("BOTANY INSPECT — DEVELOPMENT ONLY");out.add(heading);out.add("Location: "+dimension+" "+pos.getX()+", "+pos.getY()+", "+pos.getZ()+"; tick="+tick+"; plant revision="+revision); fields.forEach((k,v)->out.add(k+": "+v)); if(!diff.isEmpty()){out.add("Changed since prior sample (not mutation provenance):");out.addAll(diff);}out.add("Captured mutation records describe carried descriptors, not provenance, history, or action cause.");out.add("Profile capabilities/targets are not activated effects; recorded tray outcomes are not per-plant mutation history.");return List.copyOf(out); }
    }
}
