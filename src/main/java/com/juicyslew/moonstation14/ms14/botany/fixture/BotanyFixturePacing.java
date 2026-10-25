package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.ms14.botany.HydroponicsTrayBlockEntity;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/** Transient fixture pacing over the existing tray timing owner. */
public final class BotanyFixturePacing {
    public record StationSet(String name, List<Integer> stations) {
        public StationSet { stations=List.copyOf(stations); }
        public int first() { return stations.get(0); }
        public int last() { return stations.get(stations.size()-1); }
    }
    private static StationSet set(String name,int... nums) { return new StationSet(name,java.util.Arrays.stream(nums).boxed().toList()); }
    private static final List<StationSet> SETS = List.of(
            set("baseline",1,2),set("core",3,4),set("mutagen",5,6,7),set("harvest",8,9,10),
            set("sampling",11,12,13),set("resources",1,14,15),set("timing",1,11,16,17,18),
            set("care-health",19,20,21,22),set("care-genes",23,24,25),set("care-growth",26,27,28,29),
            set("traits",2,30,31,32),set("toxins",33,34),set("pests",35,36),set("weeds",37,38),
            set("heat",39,40),set("viability",41,42),set("lifespan",43,44),set("pressure",45,46),
            set("products",47,48,49,50),set("gases",51,52));
    private BotanyFixturePacing() { }
    public static List<StationSet> sets() { return SETS; }
    public static StationSet named(String name) { return SETS.stream().filter(s->s.name().equalsIgnoreCase(name)).findFirst().orElse(null); }
    public static List<HydroponicsTrayBlockEntity> trays(ServerLevel level, BlockPos origin) {
        var result=new ArrayList<HydroponicsTrayBlockEntity>();
        for(var station:BotanyFixtureStations.all()) if(level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,station.legacyId())) instanceof HydroponicsTrayBlockEntity tray) result.add(tray);
        return result;
    }
    public static void pauseAll(ServerLevel level, BlockPos origin) { trays(level,origin).forEach(t->t.setFixturePaused(true)); }
    public static void activate(ServerLevel level, BlockPos origin, int first, int last) {
        for(var station:BotanyFixtureStations.all()) if(level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,station.legacyId())) instanceof HydroponicsTrayBlockEntity tray)
            tray.setFixturePaused(station.number()<first||station.number()>last);
    }
    public static void activate(ServerLevel level,BlockPos origin,StationSet selected) {
        var active=java.util.Set.copyOf(selected.stations());
        for(var station:BotanyFixtureStations.all()) if(level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,station.legacyId())) instanceof HydroponicsTrayBlockEntity tray)
            tray.setFixturePaused(!active.contains(station.number()));
    }
    public static String pausedStatus(ServerLevel level, BlockPos origin) {
        var active=BotanyFixtureStations.all().stream().filter(s->level.getBlockEntity(SmallBotanyFixtureLayout.stationTray(origin,s.legacyId())) instanceof HydroponicsTrayBlockEntity t&&!t.fixturePaused()).map(BotanyFixtureStations.Station::number).toList();
        long paused=52-active.size();
        return paused+"/52 stations paused; active="+(active.isEmpty()?"none":active.toString());
    }
}
