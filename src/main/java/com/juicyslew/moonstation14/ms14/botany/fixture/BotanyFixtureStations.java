package com.juicyslew.moonstation14.ms14.botany.fixture;

import java.util.*;

/** Single source of truth for the walking order and legacy station identities. */
public final class BotanyFixtureStations {
    public record Station(String legacyId, int number, int room, int slot) { }
    private static final List<List<String>> ROOM_IDS = List.of(
            List.of("D","E","F","G","H","I","J","A","B","C","V","W","X","Y","Z","AA","AC","AD"),
            List.of("K","L","M","N","O","P","Q","R","S","T","U","AB","AE","AL","AF","AG","AH","AI","AJ","AK","AM","AN"),
            List.of("AO","AP","AQ","AR","AS","AT","AU","AV","AW","AX","AY","AZ"));
    private static final List<Station> ALL;
    private static final Map<String, Station> BY_ID;
    static {
        var all = new ArrayList<Station>(); var byId = new LinkedHashMap<String, Station>();
        for (int room=0; room<ROOM_IDS.size(); room++) for (int slot=0; slot<ROOM_IDS.get(room).size(); slot++) {
            var station = new Station(ROOM_IDS.get(room).get(slot), all.size()+1, room, slot);
            all.add(station); byId.put(station.legacyId(), station);
        }
        ALL=List.copyOf(all); BY_ID=Map.copyOf(byId);
    }
    public static List<Station> all() { return ALL; }
    public static Station lookup(String legacyId) { return BY_ID.get(legacyId); }
    public static String displayTitle(String oldTitle) {
        String id=oldTitle.split(" ",2)[0]; Station s=lookup(id);
        if(s==null) throw new IllegalArgumentException("Unknown station: "+id);
        int space=oldTitle.indexOf(' '); return String.format(Locale.ROOT,"%02d %s",s.number(),space<0?"":oldTitle.substring(space+1));
    }
    private BotanyFixtureStations() { }
}
