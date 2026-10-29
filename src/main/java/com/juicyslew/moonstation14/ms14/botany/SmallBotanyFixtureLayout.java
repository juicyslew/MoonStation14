package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Compact bounded layout shared by the admin builder, scenarios, and fixture checks. */
public final class SmallBotanyFixtureLayout {
    public static final int WIDTH = 23, DEPTH = 29, HEIGHT = 5;
    public static final int SHOWCASE_STATIONS = 12;
    public record ConnectorCell(BlockPos pos, boolean opening) { }
    public record CareLightingCableNode(BlockPos pos, net.minecraft.core.Direction face) { }
    /** Care showcase uses the same bounded footprint, offset east of the established operator room. */
    public static BlockPos careOrigin(BlockPos o) { return o.offset(WIDTH + 2, 0, 0); }
    public static BlockPos showcaseOrigin(BlockPos main) { return main.offset(2 * (WIDTH + 2), 0, 0); }
    public static java.util.List<BlockPos> roomOrigins(BlockPos main) { return java.util.List.of(main, careOrigin(main), showcaseOrigin(main)); }
    public static BlockPos showcaseTray(BlockPos main, int index) {
        if(index<0||index>=SHOWCASE_STATIONS) throw new IllegalArgumentException("showcase station index");
        return stationTray(main, com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.all().stream().filter(s->s.room()==2).toList().get(index).legacyId());
    }
    public static BlockPos showcaseSign(BlockPos main, int index) { return showcaseTray(main,index).south(); }
    public static BlockPos showcaseLamp(BlockPos main) { return showcaseOrigin(main).offset(7,3,2); }
    public static BlockPos careTray(BlockPos wing, int station) {
        if (station < 0 || station >= 11) throw new IllegalArgumentException("care station index");
        return stationTray(wing.offset(-(WIDTH+2),0,0), "KLMNOPQRSTU".substring(station,station+1));
    }
    public static BlockPos careSign(BlockPos wing, int station) { return careTray(wing, station).south(); }
    public static BlockPos stationTray(BlockPos main, String id) {
        var station=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations.lookup(id);
        if(station==null) throw new IllegalArgumentException("unknown station "+id);
        BlockPos room=roomOrigins(main).get(station.room());
        int row=station.slot()/4, col=station.slot()%4;
        if((row&1)!=0) col=3-col;
        return room.offset(new int[]{3,8,13,18}[col],1,new int[]{5,9,13,17,21,25}[row]);
    }
    public static BlockPos stationSign(BlockPos main,String id) { return stationTray(main,id).south(); }
    public static BlockPos stationTorch(BlockPos main,String id) { return stationTray(main,id).offset(1,0,1); }
    /** Distinct clear floor cells in the station bay, away from its tray, sign, torch and front approach. */
    public static BlockPos supplyPositionNear(BlockPos station, int index) {
        if (index < 0 || index >= 6) throw new IllegalArgumentException("supply index");
        int[][] offsets={{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{2,0}};
        return station.offset(offsets[index][0],0,offsets[index][1]);
    }
    /** Six tolerance-preset trays occupy audited free cells in the existing care wing. */
    public static BlockPos toleranceTray(BlockPos wing, int station) {
        return stationTray(wing.offset(-(WIDTH+2),0,0),new String[]{"AF","AG","AH","AI","AJ","AK","AM","AN"}[checkedTolerance(station)]);
    }
    public static BlockPos toleranceSign(BlockPos wing,int station) {
        return stationSign(wing.offset(-(WIDTH+2),0,0),new String[]{"AF","AG","AH","AI","AJ","AK","AM","AN"}[checkedTolerance(station)]);
    }
    private static int checkedTolerance(int station) { if(station<0||station>=8) throw new IllegalArgumentException("tolerance station index"); return station; }
    /** Exact bounded connector construction/air plan shared by preflight, build, and finite-air setup. */
    public static java.util.List<ConnectorCell> connectorPlan(BlockPos main) {
        var cells=new java.util.ArrayList<ConnectorCell>();
        for(int start : connectorStarts(main)) for(int x=start;x<=start+1;x++) {
            cells.add(new ConnectorCell(main.offset(x,0,5),false));
            cells.add(new ConnectorCell(main.offset(x,3,5),false));
            for(int y=1;y<=2;y++) {
                cells.add(new ConnectorCell(main.offset(x,y,4),false));
                cells.add(new ConnectorCell(main.offset(x,y,5),true));
                cells.add(new ConnectorCell(main.offset(x,y,6),false));
            }
        }
        for(int start : connectorStarts(main)) for(int x:new int[]{start-1,start+2}) for(int y=1;y<=2;y++) cells.add(new ConnectorCell(main.offset(x,y,5),true));
        return java.util.List.copyOf(cells);
    }
    private static java.util.List<Integer> connectorStarts(BlockPos main) {
        return roomOrigins(main).subList(0, roomOrigins(main).size()-1).stream()
                .map(room -> room.getX()-main.getX()+WIDTH).toList();
    }
    /** Complete set of cells the fixture builder may write; command preflight and pure tests share it. */
    public static java.util.List<BlockPos> plannedFootprint(BlockPos main) {
        var positions=new java.util.LinkedHashSet<BlockPos>();
        for(BlockPos room:roomOrigins(main)) positions.addAll(roomFootprint(room,WIDTH,DEPTH,HEIGHT));
        connectorPlan(main).forEach(cell->positions.add(cell.pos()));
        careLightingCablePlan(main).forEach(node->positions.add(node.pos()));
        for(var node:showcaseLightingCablePlan(main)) positions.add(node.pos());
        for(int x=5;x<=9;x++) for(int y=0;y<=3;y++) for(int z=-4;z<=-1;z++) positions.add(main.offset(x,y,z));
        return java.util.List.copyOf(positions);
    }
    public static java.util.List<BlockPos> roomFootprint(BlockPos origin,int width,int depth,int height) {
        var positions=new java.util.ArrayList<BlockPos>(width*depth*height);
        for(int x=0;x<width;x++) for(int y=0;y<height;y++) for(int z=0;z<depth;z++) positions.add(origin.offset(x,y,z));
        return java.util.List.copyOf(positions);
    }
    private SmallBotanyFixtureLayout() { }
    public static BlockPos normalTray(BlockPos o, boolean second) { return stationTray(o,second?"D":"E"); }
    public static BlockPos dryTray(BlockPos o, boolean second) { return stationTray(o,second?"G":"F"); }
    public static BlockPos untouchedTray(BlockPos o) { return stationTray(o,"H"); }
    public static BlockPos statsMutationTray(BlockPos o) { return stationTray(o,"I"); }
    public static BlockPos gasExchangeTray(BlockPos o) { return stationTray(o,"J"); }
    public static BlockPos grinder(BlockPos o) { return o.offset(7, 1, 2); }
    public static BlockPos extractor(BlockPos o) { return o.offset(9, 1, 3); }
    public static BlockPos apc(BlockPos o) { return o.offset(7, 1, 1); }
    public static BlockPos lamp(BlockPos o) { return o.offset(7, 3, 2); }
    public static BlockPos careLamp(BlockPos wing) { return wing.offset(7, 3, 2); }
    /** APC-face turn into the care wing, followed by an uninterrupted south-facing cable run. */
    public static java.util.List<CareLightingCableNode> careLightingCablePlan(BlockPos main) {
        var nodes = new java.util.ArrayList<CareLightingCableNode>();
        nodes.add(new CareLightingCableNode(main.offset(7, 3, 1), net.minecraft.core.Direction.SOUTH));
        for (int x = 8; x <= WIDTH + 2; x++) nodes.add(new CareLightingCableNode(main.offset(x, 3, 1), net.minecraft.core.Direction.SOUTH));
        return java.util.List.copyOf(nodes);
    }
    public static java.util.List<CareLightingCableNode> showcaseLightingCablePlan(BlockPos main) {
        var nodes=new java.util.ArrayList<CareLightingCableNode>();
        for(int x=WIDTH+3;x<=2*(WIDTH+2)+7;x++) nodes.add(new CareLightingCableNode(main.offset(x,3,1),net.minecraft.core.Direction.SOUTH));
        return java.util.List.copyOf(nodes);
    }
    public static BlockPos sample(BlockPos o) { return o.offset(6, 2, 3); }
    public static BlockPos noRepeatTray(BlockPos o) { return stationTray(o,"A"); }
    public static BlockPos repeatTray(BlockPos o) { return stationTray(o,"B"); }
    public static BlockPos selfHarvestTray(BlockPos o) { return stationTray(o,"C"); }
    public static BlockPos samplingTray(BlockPos o) { return stationTray(o,"V"); }
    public static BlockPos samplingSign(BlockPos o) { return stationSign(o,"V"); }
    public static BlockPos yieldTray(BlockPos o) { return stationTray(o,"W"); }
    public static BlockPos yieldSign(BlockPos o) { return stationSign(o,"W"); }
    public static BlockPos potencyTray(BlockPos o) { return stationTray(o,"X"); }
    public static BlockPos potencySign(BlockPos o) { return stationSign(o,"X"); }
    public static BlockPos waterTray(BlockPos o) { return stationTray(o,"Y"); }
    public static BlockPos waterSign(BlockPos o) { return stationSign(o,"Y"); }
    public static BlockPos nutrientTray(BlockPos o) { return stationTray(o,"Z"); }
    public static BlockPos nutrientSign(BlockPos o) { return stationSign(o,"Z"); }
    public static BlockPos fastStageTray(BlockPos o) { return stationTray(o,"AA"); }
    public static BlockPos fastStageSign(BlockPos o) { return stationSign(o,"AA"); }
    public static BlockPos earlyFruitTray(BlockPos wing) { return stationTray(wing.offset(-(WIDTH+2),0,0),"AB"); }
    public static BlockPos earlyFruitSign(BlockPos wing) { return stationSign(wing.offset(-(WIDTH+2),0,0),"AB"); }
    public static BlockPos enduranceTray(BlockPos o) { return stationTray(o,"AC"); }
    public static BlockPos enduranceSign(BlockPos o) { return stationSign(o,"AC"); }
    /** South-edge pair keeps the row-9 approach clear while leaving the side sign east of the tray. */
    public static BlockPos idealHeatTray(BlockPos o) { return stationTray(o,"AD"); }
    public static BlockPos idealHeatSign(BlockPos o) { return stationSign(o,"AD"); }
    public static BlockPos lowPressureTray(BlockPos wing) { return stationTray(wing.offset(-(WIDTH+2),0,0),"AE"); }
    public static BlockPos lowPressureSign(BlockPos wing) { return stationSign(wing.offset(-(WIDTH+2),0,0),"AE"); }
    public static BlockPos ligneousTray(BlockPos wing) { return stationTray(wing.offset(-(WIDTH+2),0,0),"AL"); }
    public static BlockPos ligneousSign(BlockPos wing) { return stationSign(wing.offset(-(WIDTH+2),0,0),"AL"); }
    public static Vec3 entry(BlockPos o) { return new Vec3(o.getX() + 6.5, o.getY() + 1, o.getZ() + 3.5); }
}
