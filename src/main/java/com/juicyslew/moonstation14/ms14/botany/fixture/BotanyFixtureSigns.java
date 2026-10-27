package com.juicyslew.moonstation14.ms14.botany.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;

import java.util.List;

/** Short physical station labels owned only by the bounded operator fixture. */
public final class BotanyFixtureSigns {
    public enum Classification { PRESET_COMPARISON, GENETIC_PRESET, ORDINARY_TRIGGER }
    public record Label(BlockPos pos, BlockPos trayPos, String title, Classification classification,
                        String demonstration, String action, String result) {
        public Label { if (!pos.equals(trayPos.south())) throw new IllegalArgumentException("sign must be south of tray"); }
        public String displayTitle() { return BotanyFixtureStations.displayTitle(title); }
        public String[] lines() { return new String[]{displayTitle(), classification == Classification.ORDINARY_TRIGGER ? "TRIGGER" : "PRESET", action, result}; }
        public String summary() { return displayTitle() + " — " + demonstration + "; " + action + "; " + result; }
        /** South-facing signs consistently face the approach from the aisle. */
        public int rotation() { return 0; }
    }
    /** Navigation signs are separate from station labels and derive ranges from the canonical room catalog. */
    public record Guide(BlockPos pos, boolean wall, int rotation, List<String> lines) { }
    public static List<Guide> guides(BlockPos main) {
        var out = new java.util.ArrayList<Guide>();
        var roomOrigins=List.of(main, SmallBotanyFixtureLayout.careOrigin(main), SmallBotanyFixtureLayout.showcaseOrigin(main));
        var stations=BotanyFixtureStations.all();
        for(int i=0;i<roomOrigins.size();i++) {
            final int room=i;
            int first=stations.stream().filter(s->s.room()==room).mapToInt(BotanyFixtureStations.Station::number).min().orElseThrow();
            int last=stations.stream().filter(s->s.room()==room).mapToInt(BotanyFixtureStations.Station::number).max().orElseThrow();
            BlockPos origin=roomOrigins.get(i);
             out.add(new Guide(origin.offset(11,1,2),false,0,List.of("ROOM "+(i+1),String.format(java.util.Locale.ROOT,"%02d-%02d",first,last),"Follow numbers","Examine Sh+LMB")));
             if(i==0) out.add(new Guide(origin.offset(10,2,2),true,4,List.of("Starts paused","/ms14","botany_fixture","start core")));
             if(i<2) { final int next=i+1; out.add(new Guide(origin.offset(SmallBotanyFixtureLayout.WIDTH-2,2,4),true,4,List.of("NEXT ROOM E",String.format(java.util.Locale.ROOT,"%02d-%02d",stations.stream().filter(s->s.room()==next).mapToInt(BotanyFixtureStations.Station::number).min().orElseThrow(),stations.stream().filter(s->s.room()==next).mapToInt(BotanyFixtureStations.Station::number).max().orElseThrow()),"Start: /ms14","fixture start"))); }
        }
        return List.copyOf(out);
    }
    /** Place navigation signs only after the complete plan has passed preflight. */
    public static void placeGuides(ServerLevel level, BlockPos origin) {
        for (Guide guide : guides(origin)) {
            var state = guide.wall()
                    ? Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING, net.minecraft.core.Direction.WEST)
                    : Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, guide.rotation());
            level.setBlock(guide.pos(), state, 3);
            if (level.getBlockEntity(guide.pos()) instanceof SignBlockEntity sign) {
                var text=sign.getFrontText();
                for(int i=0;i<guide.lines().size();i++) text=text.setMessage(i,Component.literal(guide.lines().get(i)));
                sign.setText(text,true);
                var back=sign.getBackText();
                for(int i=0;i<guide.lines().size();i++) back=back.setMessage(i,Component.literal(guide.lines().get(i)));
                sign.setText(back,false);
                sign.setChanged();
            }
        }
    }
    public record CareDose(String station, String reagent, long cents) {
        public String displayStationName() { return BotanyFixtureStations.displayTitle(station); }
    }
    public record MutationShowcaseCoverage(String mutation, List<String> stations, String action, String expected, boolean supported) { }
    private static final List<CareDose> CARE_DOSES=List.of(
            new CareDose("K HEALTH","ammonia",100),new CareDose("L TOXINS","pestkiller",100),
            new CareDose("M PESTS","pestkiller",100),new CareDose("N WEEDS","weedkiller",100),
            new CareDose("O POTENCY","sedin",100),new CareDose("P HARVEST","robustharvest",100),
            new CareDose("Q CRYO","cryoxadone",100),new CareDose("R GROWTH","diethylamine",1000),
            new CareDose("S GENES","diethylamine",1000),new CareDose("T PHALANX","phalanximine",400),
            new CareDose("U SEEDS","sedin",1000));
    public static List<CareDose> careDoses() { return CARE_DOSES; }
    public static List<MutationShowcaseCoverage> mutationShowcaseCoverage() {
        return List.of(
                coverage("Unviable","AP","Wait first ordinary cycle","Dead / matched control alive",true), coverage("ChangeWaterConsumption","Y","Wait/examine","Trend vs D",true), coverage("ChangeNutrientConsumption","Z","Wait/examine","Trend vs D",true), coverage("ChangeIdealHeat","AD","Wait/examine","Room warning vs D",true), coverage("ChangeHeatTolerance","AM, AN","Wait first ordinary cycle","AM dead / AN living",true), coverage("ChangeToxinsTolerance","AF, AG","Wait first ordinary cycle","AF dead / AG living",true), coverage("ChangeLowPressureTolerance","AE","Wait/examine","Room warning vs D",true), coverage("ChangeHighPressureTolerance","AT","Wait/examine","Room warning",true), coverage("ChangePestTolerance","AH, AI","Wait first ordinary cycle","AH dead / AI living",true), coverage("ChangeWeedTolerance","AJ, AK","Wait first ordinary cycle","AJ dead / AK living",true), coverage("ChangeEndurance","AC","Sample; inspect","Damage warning / inspector",true), coverage("ChangeYield","W","Harvest/extract","Packet Y7 P5",true), coverage("ChangeLifespan","AR","Wait/examine","Inspector lifespan",true), coverage("ChangeMaturation","AA","Compare with V","Stage projection",true), coverage("ChangeProduction","AB","Harvest/wait","Earlier readiness",true), coverage("ChangePotency","X","Harvest/extract","Packet Y3 P50",true), coverage("ChangeSeedless","U","Harvest/extract","Harvest; extract denied",true), coverage("Lignification","AL","Hand then knife harvest","Hand denied / knife succeeds",true), coverage("ChangeScreaming","V","Wirecutter sample","Packet and scream",true), coverage("ChangeChemicals","AV","Harvest/grind","Inspector reagent + grind",true), coverage("ChangeExudeGasses","AZ","Wait/examine","Hidden gas gene",true), coverage("ChangeConsumeGasses","AY","Wait/examine","Hidden gas gene",true), coverage("ChangeHarvest","A, B, C","Harvest/wait","Mode-specific result",true), coverage("ChangeSpecies","AW, AX","Harvest/inspect","Identity only",true), coverage("Slippery","AU","Harvest/drop/walk over","Slip/stun/contact sound",true), coverage("Sentient","","Unsupported","Unsupported",false), coverage("Kudzufication","","Unsupported","Unsupported",false));
    }
    private static MutationShowcaseCoverage coverage(String gene,String stations,String action,String expected,boolean supported) { return new MutationShowcaseCoverage(gene,stations.isEmpty()?List.of():List.of(stations.split(", ")),action,expected,supported); }
    /** All fixture reagent bottles; care station ordering remains the canonical eleven-dose layout. */
    public static List<CareDose> allFixtureDoses() {
        var result = new java.util.ArrayList<CareDose>();
        result.add(new CareDose("I MUTAGEN", "unstablemutagen", 500));
        result.addAll(CARE_DOSES);
        return List.copyOf(result);
    }
    private BotanyFixtureSigns() { }

    /** Player-triggered small-dose care stations; presets describe the starting plant only. */
    public static List<Label> careLabels(BlockPos wing) {
        String[] outcomes={"Care may help","Toxins target","Pests target","Weeds target",
                "Potency lower","Potency rises","Age/health","Growth advances","Rolls not shown",
                "Plant revives","Extract to test"};
        String[] details={"health/nutrition may rise","toxins fall; pests may fall","pests fall; toxins may fall","weeds fall",
                "potency may fall; seed restoration roll is a no-op on this seeded plant","potency rises; yield may fall and seedlessness may begin",
                "health/toxins change; mature age rewinds","growth may advance; repeat may be needed","two independent lifespan/endurance gene rolls; the 10% lifespan/endurance change cannot be verified by ordinary manual inspection",
                "unviable plant returns to viable","seedless plant may regain seeds; harvest/extract; a successful real packet proves restoration, but its numeric fields do not. A failed crop is not updated after later dosing; use a fresh harvest"};
        var result = new java.util.ArrayList<Label>();
        for (int i=0; i<CARE_DOSES.size(); i++) {
            var r = CARE_DOSES.get(i);
            result.add(new Label(SmallBotanyFixtureLayout.careSign(wing,i), SmallBotanyFixtureLayout.careTray(wing,i),
                    r.station(), Classification.ORDINARY_TRIGGER, r.reagent()+" care from a prepared plant; "+(r.cents()/100)+"u bottle; apply in 1u/4u transfers (1u bottle once; 4u bottle once; 10u bottle twice); "+details[i], "Dose",
                    outcomes[i]));
        }
        return List.copyOf(result);
    }
    public static List<Label> toleranceLabels(BlockPos wing) {
        String[] titles={"AF TOXIN 4","AG TOXIN 10","AH PEST 5","AI PEST 15","AJ WEED 5","AK WEED 15","AM HEAT 10","AN HEAT 25"};
        String[] descriptions={"Healthy wheat, toxins 5, tolerance 4", "Healthy wheat, toxins 5, tolerance 10",
                "Healthy wheat, pests 6, tolerance 5", "Healthy wheat, pests 6, tolerance 15",
                "Healthy wheat, weeds 6, tolerance 5", "Healthy wheat, weeds 6, tolerance 15",
                "Healthy wheat, ideal heat 275 K, tolerance 10 K; room 293.15 K", "Healthy wheat, ideal heat 275 K, tolerance 25 K; room 293.15 K"};
        var out=new java.util.ArrayList<Label>();
        for(int i=0;i<8;i++) {
            String paired=titles[i^1].split(" ",2)[0];
            int number=BotanyFixtureStations.lookup(paired).number();
            String result="Compare "+String.format(java.util.Locale.ROOT,"%02d",number);
            out.add(new Label(SmallBotanyFixtureLayout.toleranceSign(wing,i),SmallBotanyFixtureLayout.toleranceTray(wing,i),titles[i],Classification.GENETIC_PRESET,
                    descriptions[i]+"; health/resources full", "Wait over time", result));
        }
        return List.copyOf(out);
    }

    public static List<Label> labels(BlockPos o) {
        var mainLabels = new java.util.ArrayList<Label>(List.of(
                label(SmallBotanyFixtureLayout.normalTray(o, true), "D WHEAT", Classification.ORDINARY_TRIGGER, "Normal wheat control; leave unharvested through comparisons", "Examine; keep", "For 14/15/18"),
                label(SmallBotanyFixtureLayout.normalTray(o, false), "E TOMATO", Classification.ORDINARY_TRIGGER, "Normal tomato harvest", "Harvest once", "Plant stays"),
                label(SmallBotanyFixtureLayout.dryTray(o, false), "F WEEDS", Classification.ORDINARY_TRIGGER, "Weeds are intentionally present; hoe clears weeds", "Use hoe", "Weeds clear"),
                label(SmallBotanyFixtureLayout.dryTray(o, true), "G CARE", Classification.ORDINARY_TRIGGER, "No nutrients: warning demonstration", "Try water/nutr.", "Check warnings"),
                label(SmallBotanyFixtureLayout.untouchedTray(o), "H CHEMISTRY", Classification.ORDINARY_TRIGGER, "Vestine metabolism", "Wait/examine", "Harvest/grind"),
                label(SmallBotanyFixtureLayout.statsMutationTray(o), "I MUTAGEN", Classification.ORDINARY_TRIGGER, "Modifier-3 preset; probabilistic mutation, no guaranteed outcome", "Dose mutagen", "Examine plant"),
                label(SmallBotanyFixtureLayout.gasExchangeTray(o), "J GAS", Classification.PRESET_COMPARISON, "Preset nitrogen/oxygen genes", "Wait/examine", "No success cue"),
                label(SmallBotanyFixtureLayout.noRepeatTray(o), "A NO-REPEAT", Classification.PRESET_COMPARISON, "Wheat harvest-mode preset", "Harvest once", "Plant clears"),
                label(SmallBotanyFixtureLayout.repeatTray(o), "B REPEAT", Classification.PRESET_COMPARISON, "Wheat harvest-mode preset", "Harvest once", "Plant stays"),
                label(SmallBotanyFixtureLayout.selfHarvestTray(o), "C SELF-HARVEST", Classification.PRESET_COMPARISON, "Wheat harvest-mode preset", "Wait for crop", "Drops appear"),
                new Label(SmallBotanyFixtureLayout.samplingSign(o), SmallBotanyFixtureLayout.samplingTray(o), "V SAMPLE", Classification.PRESET_COMPARISON,
                        "Resolved wheat CanScream preset; sample once with retained wirecutter. Packet may go to hand or world; plant it in station 08 after its ordinary no-repeat harvest has cleared it.",
                         "Sample cutter", "Packet + scream"),
                new Label(SmallBotanyFixtureLayout.yieldSign(o), SmallBotanyFixtureLayout.yieldTray(o), "W YIELD", Classification.GENETIC_PRESET,
                        "Wheat yield preset 7; potency 5, no-repeat", "Harvest/extract", "Packet: Y7 P5"),
                new Label(SmallBotanyFixtureLayout.potencySign(o), SmallBotanyFixtureLayout.potencyTray(o), "X POTENCY", Classification.GENETIC_PRESET,
                        "Wheat potency preset 50; yield 3, no-repeat", "Harvest/extract", "Packet: Y3 P50"),
                new Label(SmallBotanyFixtureLayout.waterSign(o),SmallBotanyFixtureLayout.waterTray(o),"Y WATER",Classification.GENETIC_PRESET,
                        "Wheat water-use preset 0.9 (resolved base 0.5); no-repeat", "Wait/examine", "Compare 01"),
                new Label(SmallBotanyFixtureLayout.nutrientSign(o),SmallBotanyFixtureLayout.nutrientTray(o),"Z NUTRIENT",Classification.GENETIC_PRESET,
                        "Wheat nutrient-use preset 1.2 (resolved base 0.4); no-repeat", "Wait/examine", "Compare 01"),
                new Label(SmallBotanyFixtureLayout.fastStageSign(o),SmallBotanyFixtureLayout.fastStageTray(o),"AA FAST STAGES",Classification.GENETIC_PRESET,
                        "Wheat maturation 3 vs 6; same age as station 11. Stage only, not earlier harvest", "Compare 11", "Both not ready"),
                new Label(SmallBotanyFixtureLayout.earlyFruitSign(SmallBotanyFixtureLayout.careOrigin(o)),
                        SmallBotanyFixtureLayout.earlyFruitTray(SmallBotanyFixtureLayout.careOrigin(o)),"AB EARLY FRUIT",Classification.GENETIC_PRESET,
                        "Tomato repeat preset; production 1 vs station 02 baseline 6", "Harvest 02 + 30", "Regrows"),
                new Label(SmallBotanyFixtureLayout.enduranceSign(o),SmallBotanyFixtureLayout.enduranceTray(o),"AC ENDURANCE",Classification.GENETIC_PRESET,
                        "Wheat maximum endurance 50 vs station 11 baseline 100; same stage/soil/full-health setup", "Sample once", "Health warning"),
                new Label(SmallBotanyFixtureLayout.idealHeatSign(o),SmallBotanyFixtureLayout.idealHeatTray(o),"AD HEAT 263K",Classification.GENETIC_PRESET,
                        "Resolved wheat changes only ideal heat 293 to 263 K; normal room air is about 293 K", "Wait/examine", "Compare 01"),
                new Label(SmallBotanyFixtureLayout.lowPressureSign(SmallBotanyFixtureLayout.careOrigin(o)),
                        SmallBotanyFixtureLayout.lowPressureTray(SmallBotanyFixtureLayout.careOrigin(o)),"AE LOW 100KPA",Classification.GENETIC_PRESET,
                        "Resolved wheat changes only low-pressure threshold 81 to 100 kPa; normal room is about 92.6 kPa", "Wait/examine", "Compare 01"),
                new Label(SmallBotanyFixtureLayout.ligneousSign(SmallBotanyFixtureLayout.careOrigin(o)),
                        SmallBotanyFixtureLayout.ligneousTray(SmallBotanyFixtureLayout.careOrigin(o)),"AL LIGNEOUS",Classification.GENETIC_PRESET,
                          "Resolved wheat ligneous preset; ordinary Sharp-gated harvest", "Hand then knife", "Knife harvests")));
        mainLabels.addAll(mutationShowcaseLabels(o));
        return List.copyOf(mainLabels);
    }
    private static List<Label> mutationShowcaseLabels(BlockPos main) {
        String[] titles={"AO VIABLE","AP UNVIABLE","AQ LIFE 25","AR LIFE 5","AS PRESS 121","AT PRESS 85","AU SLIPPERY","AV CHEMICAL","AW WHEAT","AX MEAT WHEAT","AY NITROGEN","AZ OXYGEN"};
        String[] demos={"Healthy viable wheat; full resources","Healthy wheat; unviable preset","Healthy lifespan 25 control; aged 6","Healthy lifespan 5 preset; aged 6","Healthy max pressure 121 control","Healthy max pressure 85 preset; room 92.6 kPa","Resolved persistent slippery produce gene; no puddle","Resolved EvilRandomFill preset; inspect reagent and grind produce","Wheat source species; inspect packet/product identity","Real reachable meat-wheat target; inspect identity only","Nitrogen consume-only preset","Oxygen exude-only preset"};
        String[] actions={"Wait 1 poll","Wait 1 poll","Wait/examine","Wait/examine","Wait/examine","Wait/examine","Harvest, drop","Harvest/grind","Harvest/inspect","Harvest/inspect","Wait/examine","Wait/examine"};
        String[] results={"Compare 41","Observe warning","Compare 43","Age stress","Compare 45","Pressure risk","Slip/stun/sound","Reagent + grind","Wheat identity","Target identity","Hidden gas gene","Hidden gas gene"};
        var out=new java.util.ArrayList<Label>();
        for(int i=0;i<12;i++) {
            String station=titles[i].substring(0,2);
            var coverage=mutationShowcaseCoverage().stream().filter(c->c.stations().contains(station)).findFirst();
            String detail=coverage.map(c->"; "+c.action()+" → "+c.expected()).orElse("");
            out.add(new Label(SmallBotanyFixtureLayout.showcaseSign(main,i),SmallBotanyFixtureLayout.showcaseTray(main,i),titles[i],Classification.GENETIC_PRESET,demos[i]+detail,actions[i],results[i]));
        }
        return List.copyOf(out);
    }
    private static Label label(BlockPos tray, String title, Classification classification,
                               String demonstration, String action, String result) {
        return new Label(tray.south(), tray, title, classification, demonstration, action, result);
    }
    public static void place(ServerLevel level, BlockPos origin) {
        String unsafe = placementProblem(level, origin);
        if (unsafe != null) throw new IllegalStateException("Refused fixture signs: " + unsafe);
        for (Label label : labels(origin)) {
            level.setBlock(label.pos(), Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION, label.rotation()), 3);
            if (level.getBlockEntity(label.pos()) instanceof SignBlockEntity sign) {
                var text = sign.getFrontText();
                for (int i = 0; i < label.lines().length; i++) text = text.setMessage(i, Component.literal(label.lines()[i]));
                sign.setText(text, true);
                var back = sign.getBackText();
                for (int i = 0; i < label.lines().length; i++) back = back.setMessage(i, Component.literal(label.lines()[i]));
                sign.setText(back, false);
                sign.setChanged();
            }
        }
    }

    /** Validate the whole sign set before placing any sign; never replace an occupied block/entity. */
    public static String placementProblem(ServerLevel level, BlockPos origin) {
        var labels = labels(origin);
        var allLabels=new java.util.ArrayList<>(labels);
        allLabels.addAll(careLabels(SmallBotanyFixtureLayout.careOrigin(origin)));
        allLabels.addAll(toleranceLabels(SmallBotanyFixtureLayout.careOrigin(origin)));
        var signPositions = new java.util.HashSet<BlockPos>();
        for (Label label : allLabels) {
            if (!signPositions.add(label.pos())) return "duplicate sign position " + label.pos();
            if (!level.getBlockState(label.pos()).isAir() || level.getBlockEntity(label.pos()) != null)
                return "sign space is not empty at " + label.pos();
            if (Math.abs(label.pos().getX() - label.trayPos().getX()) + Math.abs(label.pos().getZ() - label.trayPos().getZ()) != 1)
                return "station arrow is not adjacent to its tray at " + label.pos();
            if (!(level.getBlockEntity(label.trayPos()) instanceof com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity))
                return "associated tray is missing at " + label.trayPos();
            if (!Blocks.OAK_SIGN.defaultBlockState().canSurvive(level, label.pos()))
                return "oak sign would not survive at " + label.pos();
        }
        for (Label label : allLabels) if (signPositions.contains(label.trayPos()))
            return "sign position overlaps its tray at " + label.trayPos();
        return null;
    }

    public static String layoutProblem(BlockPos origin) {
        var reserved = new java.util.HashSet<BlockPos>();
        for (BlockPos p : List.of(SmallBotanyFixtureLayout.normalTray(origin, false), SmallBotanyFixtureLayout.normalTray(origin, true),
                SmallBotanyFixtureLayout.dryTray(origin, false), SmallBotanyFixtureLayout.dryTray(origin, true),
                SmallBotanyFixtureLayout.untouchedTray(origin), SmallBotanyFixtureLayout.statsMutationTray(origin),
                SmallBotanyFixtureLayout.gasExchangeTray(origin), SmallBotanyFixtureLayout.yieldTray(origin), SmallBotanyFixtureLayout.potencyTray(origin),
                SmallBotanyFixtureLayout.waterTray(origin),SmallBotanyFixtureLayout.nutrientTray(origin),
                SmallBotanyFixtureLayout.fastStageTray(origin),SmallBotanyFixtureLayout.enduranceTray(origin),
                SmallBotanyFixtureLayout.idealHeatTray(origin),
                SmallBotanyFixtureLayout.lowPressureTray(SmallBotanyFixtureLayout.careOrigin(origin)),
                SmallBotanyFixtureLayout.earlyFruitTray(SmallBotanyFixtureLayout.careOrigin(origin)),SmallBotanyFixtureLayout.noRepeatTray(origin),
                SmallBotanyFixtureLayout.repeatTray(origin), SmallBotanyFixtureLayout.selfHarvestTray(origin), SmallBotanyFixtureLayout.samplingTray(origin),
                SmallBotanyFixtureLayout.grinder(origin), SmallBotanyFixtureLayout.extractor(origin), SmallBotanyFixtureLayout.apc(origin),
                 SmallBotanyFixtureLayout.lamp(origin), origin.offset(7,1,-3), origin.offset(7,1,-1),
                origin.offset(8,1,-3), origin.offset(8,1,-2), origin.offset(8,1,-1),
                origin.offset(6,1,-1), origin.offset(6,1,0), origin.offset(6,1,1),
                origin.offset(8,1,1), origin.offset(8,1,2), origin.offset(8,1,3),
                 origin.offset(8,2,2), origin.offset(8,3,2)))
            if (!reserved.add(p)) return "fixture layout contains overlapping tray/utility locations at " + p;
        for(int i=0;i<8;i++) if(!reserved.add(SmallBotanyFixtureLayout.toleranceTray(SmallBotanyFixtureLayout.careOrigin(origin),i))) return "tolerance tray overlaps fixture at "+i;
        if(!reserved.add(SmallBotanyFixtureLayout.ligneousTray(SmallBotanyFixtureLayout.careOrigin(origin)))) return "ligneous tray overlaps fixture";
        for (Label label : labels(origin)) if (reserved.contains(label.pos()))
            return "sign location overlaps a fixture tray or utility at " + label.pos();
        return null;
    }

    /** Validate support against the builder's planned solid floor, before the first world write. */
    public static String plannedLayoutProblem(BlockPos main) {
        var labels=new java.util.ArrayList<Label>();
        labels.addAll(labels(main));
        BlockPos wing=SmallBotanyFixtureLayout.careOrigin(main);
        labels.addAll(careLabels(wing));
        labels.addAll(toleranceLabels(wing));
        var footprint=SmallBotanyFixtureLayout.plannedFootprint(main);
        var positions=new java.util.HashSet<BlockPos>();
        for(Label label:labels) {
            BlockPos pos=label.pos(), support=pos.below();
            if(!positions.add(pos)) return "duplicate planned sign at "+pos;
            boolean inMain=insideRoom(main,pos), inWing=insideRoom(wing,pos), inShowcase=insideRoom(SmallBotanyFixtureLayout.showcaseOrigin(main),pos);
            if(!inMain&&!inWing&&!inShowcase) return "sign is outside a planned room at "+pos;
            if(pos.getY()!=main.getY()+1 || support.getY()!=main.getY()) return "sign is not supported by planned floor at "+pos;
            if(!footprint.contains(support)) return "sign support is outside preflight footprint at "+support;
            if(label.trayPos().equals(pos)) return "sign overlaps its tray at "+pos;
        }
        for(Guide guide:guides(main)) {
            BlockPos pos=guide.pos(), support=guide.wall()?pos.east():pos.below();
            if(!positions.add(pos)) return "duplicate planned sign at "+pos;
            if(!footprint.contains(pos)||!footprint.contains(support)) return "guide sign/support outside preflight footprint at "+pos;
            boolean room=roomOrigins(main).stream().anyMatch(r->insideRoom(r,pos));
            if(!room) return "guide sign is outside a planned room at "+pos;
            if(guide.wall() ? pos.getY()!=main.getY()+2||support.getX()!=pos.getX()+1 : pos.getY()!=main.getY()+1||support.getY()!=main.getY()) return "invalid guide sign support at "+pos;
        }
        for(Label label:labels) if(positions.contains(label.trayPos())) return "station sign overlaps a tray at "+label.trayPos();
        for(int i=0;i<8;i++) if(!footprint.contains(SmallBotanyFixtureLayout.toleranceTray(wing,i))) return "tolerance tray outside preflight footprint";
        if(!footprint.contains(SmallBotanyFixtureLayout.ligneousTray(wing))) return "ligneous tray outside preflight footprint";
        return null;
    }
    private static List<BlockPos> roomOrigins(BlockPos main) { return SmallBotanyFixtureLayout.roomOrigins(main); }
    private static boolean insideRoom(BlockPos origin,BlockPos pos) {
        return pos.getX()>=origin.getX()+1&&pos.getX()<=origin.getX()+SmallBotanyFixtureLayout.WIDTH-2
                &&pos.getY()>=origin.getY()+1&&pos.getY()<=origin.getY()+SmallBotanyFixtureLayout.HEIGHT-2
                &&pos.getZ()>=origin.getZ()+1&&pos.getZ()<=origin.getZ()+SmallBotanyFixtureLayout.DEPTH-2;
    }
}


