package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientationCandidates;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures fixture orientation selection uses the actual inherited disposal prototype ports. */
class DisposalFixtureOrientationTest {
    private static final String[] TUBES={"disposal_pipe","disposal_bend","disposal_trunk","disposal_junction","disposal_junction_flipped","disposal_y_junction","disposal_x_junction","disposal_tagger","disposal_router","disposal_router_flipped"};
    private static JsonObject json(String name){
        var stream=Objects.requireNonNull(DisposalFixtureOrientationTest.class.getResourceAsStream("/data/moonstation14/moonstation14/disposal_entity/"+name+".json"));
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static Map<ResourceLocation,JsonObject> definitions(){
        var result=new LinkedHashMap<ResourceLocation,JsonObject>();
        result.put(ModDisposals.BASE,json("disposal_tube_base"));
        for(String id:List.of("base_structure","base_machine","base_machine_powered","disposal_unit_base","disposal_unit"))result.put(ModDisposals.id(id),json(id));
        result.put(ModDisposals.HOLDER,json("disposal_holder"));
        for(String id:TUBES)result.put(ModDisposals.id(id),json(id));
        return result;
    }

    @Test void resolvedSixAxisFixturePathUsesReciprocalActualPrototypePorts(){
        var manager=new PrototypeManager();manager.register(ModDisposals.DISPOSAL_TYPE);manager.reload(ModDisposals.DISPOSAL_TYPE,definitions());
        var catalog=manager.snapshot(ModDisposals.DISPOSAL_TYPE);
        String[] ids={"disposal_trunk","disposal_pipe","disposal_bend","disposal_pipe","disposal_bend","disposal_pipe","disposal_pipe"};
        Set<Direction>[] expected=new Set[]{Set.of(Direction.SOUTH),Set.of(Direction.NORTH,Direction.SOUTH),Set.of(Direction.NORTH,Direction.UP),Set.of(Direction.DOWN,Direction.UP),Set.of(Direction.DOWN,Direction.EAST),Set.of(Direction.WEST,Direction.EAST),Set.of(Direction.WEST,Direction.EAST)};
        Direction[] path={Direction.SOUTH,Direction.SOUTH,Direction.UP,Direction.UP,Direction.EAST,Direction.EAST};
        List<DisposalEntityData.Tube> tubes=new ArrayList<>();List<DisposalOrientation> frames=new ArrayList<>();
        for(int i=0;i<ids.length;i++){
            var prototype=catalog.get(ModDisposals.id(ids[i]));assertNotNull(prototype);assertNotNull(prototype.tube());
            Set<Direction> wanted=expected[i];
            var candidates=DisposalOrientationCandidates.candidates(prototype.tube(),DisposalOrientation.identity(),wanted);
            var frame=candidates.stream().filter(candidate->worldPorts(prototype.tube(),candidate).equals(wanted)).findFirst().orElseThrow();
            tubes.add(prototype.tube());frames.add(frame);
            assertEquals(expected[i],worldPorts(prototype.tube(),frame),"resolved "+ids[i]+" at route node "+i);
        }
        for(int i=0;i<path.length;i++){
            var toward=DisposalDirection.fromMC(path[i]);
            assertTrue(DisposalTubeRouting.worldConnections(tubes.get(i),frames.get(i)).contains(toward),"node "+i+" routes forward "+path[i]);
            assertTrue(DisposalTubeRouting.worldConnections(tubes.get(i+1),frames.get(i+1)).contains(toward.opposite()),"node "+(i+1)+" reciprocates "+path[i].getOpposite());
        }
        assertEquals(Set.of(DisposalDirection.SOUTH),DisposalTubeRouting.worldConnections(tubes.getFirst(),frames.getFirst()),
                "trunk route is one SOUTH transport port; its separate unit-entry geometry is not a second route port");
    }

    @Test void boundedPlayRoomFitsFiniteEndpointProbeAndContainsUnitRouteAndSafeEntry(){
        int count=0;
        for(int x=0;x<12;x++)for(int y=0;y<7;y++)for(int z=0;z<12;z++)
            if(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(x,y,z)))count++;
         assertEquals(96,count,"6x4x4 authored finite-air volume fits trusted bounded endpoint probe capacity");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(3,0,8)),"steel floor excluded from room");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(3,5,8)),"steel roof excluded from room");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(1,2,8)),"west sealed wall excluded");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(8,2,8)),"east sealed wall excluded");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(3,2,6)),"north sealed wall excluded");
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(3,2,11)),"south sealed wall excluded");
        var required=List.of(new net.minecraft.core.BlockPos(3,2,8),new net.minecraft.core.BlockPos(3,3,8),new net.minecraft.core.BlockPos(3,1,8),
                new net.minecraft.core.BlockPos(3,1,9),new net.minecraft.core.BlockPos(3,1,10),
                new net.minecraft.core.BlockPos(3,2,10),new net.minecraft.core.BlockPos(3,3,10),
                new net.minecraft.core.BlockPos(4,3,10),new net.minecraft.core.BlockPos(5,3,10),
                new net.minecraft.core.BlockPos(6,3,10),DisposalFixtureCommands.ENTRY_CELL,DisposalFixtureCommands.ENTRY_CELL.above());
        for(var cell:required)assertTrue(DisposalFixtureCommands.isPlayAreaCell(cell),"fixture unit/route/exit/entry cell lies in bounded room: "+cell);
    }

    @Test void publicFloorReferenceMapsBackToOriginalInternalFixtureLayout(){
        var entry=new net.minecraft.core.BlockPos(22,0,24);
        var corner=DisposalFixtureCommands.cornerForEntry(entry);
        assertEquals(new net.minecraft.core.BlockPos(16,0,16),corner);
        assertEquals(new net.minecraft.core.BlockPos(22,1,24),corner.offset(DisposalFixtureCommands.ENTRY_CELL));
        assertEquals(new net.minecraft.core.BlockPos(19,2,24),corner.offset(3,2,8),"unit remains at its original world location");
        assertEquals(new net.minecraft.core.BlockPos(21,3,26),corner.offset(5,3,10),"route outlet node remains unchanged");
        assertEquals(new net.minecraft.core.BlockPos(16,0,16),entry.offset(-6,0,-8),"bounds minimum");
        assertEquals(new net.minecraft.core.BlockPos(27,6,27),entry.offset(5,6,3),"bounds maximum");
        assertTrue(DisposalFixtureCommands.isPlayAreaCell(DisposalFixtureCommands.ENTRY_CELL));
        assertTrue(DisposalFixtureCommands.isPlayAreaCell(DisposalFixtureCommands.ENTRY_CELL.above()));
        assertFalse(DisposalFixtureCommands.isPlayAreaCell(DisposalFixtureCommands.ENTRY_CELL.below()),"the floor-reference cell is enclosure floor, not room air");
        assertTrue(DisposalFixtureCommands.isPlayAreaCell(DisposalFixtureCommands.LAMP),"real lamp is inside the play-room envelope but offset from the finite-air sample directly above the unit");
        assertTrue(DisposalFixtureCommands.isPlayAreaCell(new net.minecraft.core.BlockPos(4,3,7)),"lamp cable host is in the play-room envelope");
        assertFalse(DisposalFixtureCommands.LAMP.equals(new net.minecraft.core.BlockPos(3,3,8)),"lamp must not occupy the unit's room-air source cell");
    }

    private static Set<Direction> worldPorts(DisposalEntityData.Tube tube,DisposalOrientation frame){
        var result=EnumSet.noneOf(Direction.class);
        for(var port:DisposalTubeRouting.connections(tube,frame))result.add(port.toMC());
        return Set.copyOf(result);
    }
}
