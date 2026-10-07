package com.juicyslew.moonstation14.ms14.disposal.fixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DisposalFixtureTourResolutionTest {
    private static final List<String> TUBE_IDS=List.of("disposal_pipe","disposal_bend","disposal_trunk","disposal_junction",
            "disposal_junction_flipped","disposal_y_junction","disposal_x_junction","disposal_tagger","disposal_router","disposal_router_flipped");

    @Test void resolvedTypedPlanCoversVariantsAndEveryTransitionDeterministically() {
        var catalog=loadCatalog();
        var plan=DisposalFixtureTourPlan.resolve(catalog);
        var nodes=plan.nodes();var positions=nodes.stream().map(DisposalFixtureTourPlan.Node::position).toList();
        assertEquals(259,nodes.size());
        assertEquals(nodes.size(),new HashSet<>(positions).size());
        assertEquals(10,plan.coveredTypes().size());
        for(String id:TUBE_IDS)assertTrue(plan.coveredTypes().contains(ModDisposals.id(id)),id);
        var tags=new LinkedHashSet<String>();
        for(int i=0;i<nodes.size();i++) {
            var node=nodes.get(i);
            Direction outgoing=DisposalFixtureTourPlan.outputAt(i);
            Direction incoming=DisposalFixtureTourPlan.incomingAt(i);
            var out=DisposalDirection.fromMC(outgoing);
            assertTrue(node.ports().contains(out));
            if(incoming!=null) {
                var in=DisposalDirection.fromMC(incoming);
                assertTrue(node.ports().contains(in));
                assertTrue(nodes.get(i-1).ports().contains(in.opposite()),"reciprocal mainline port at "+i);
            }
            if(!node.tag().isBlank())tags.add(node.tag());
            assertEquals(out,DisposalTubeRouting.selectNextDirection(node.effectiveTube(),node.orientation(),
                    incoming==null?null:DisposalDirection.fromMC(incoming),tags,bound->0));
            assertEquals(out,DisposalTubeRouting.selectNextDirection(node.effectiveTube(),node.orientation(),
                    incoming==null?null:DisposalDirection.fromMC(incoming),tags,bound->bound-1));
            if(node.hostKind()==com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord.HostKind.SEALED_WALL)
                assertEquals(DisposalEntityData.Kind.STRAIGHT,node.effectiveTube().kind(),"sealed host at "+node.position());
            // Every physical reciprocal edge must be the predecessor or successor; no accidental shortcuts.
            for(Direction face:Direction.values()) {
                int adjacent=positions.indexOf(node.position().relative(face));
                if(adjacent>=0&&node.ports().contains(DisposalDirection.fromMC(face))
                        &&nodes.get(adjacent).ports().contains(DisposalDirection.fromMC(face.getOpposite())))
                    assertTrue(adjacent==i-1||adjacent==i+1,"unexpected adjacent reciprocal edge from "+i+" to "+adjacent);
            }
        }
        var tagger=nodes.get(positions.indexOf(new BlockPos(6,3,10)));
        var emptyRouter=nodes.get(positions.indexOf(new BlockPos(7,3,10)));
        var matchingRouter=nodes.get(positions.indexOf(new BlockPos(14,5,1)));
        assertEquals(Set.of(),emptyRouter.routerTags());
        assertEquals(Set.of("camera tour"),matchingRouter.routerTags());
        assertEquals("camera tour",tagger.tag());
        assertEquals(25.9,plan.estimatedRideSeconds(),1e-9);
        assertEquals(.1,plan.resolvedSegmentSeconds());
    }

    private static PrototypeCatalog<DisposalEntityData> loadCatalog() {
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String id:List.of("disposal_tube_base","base_structure","base_machine","base_machine_powered","disposal_unit_base","disposal_unit","disposal_holder"))raw.put(ModDisposals.id(id),json(id));
        for(String id:TUBE_IDS)raw.put(ModDisposals.id(id),json(id));
        var manager=new PrototypeManager();manager.register(ModDisposals.DISPOSAL_TYPE);manager.reload(ModDisposals.DISPOSAL_TYPE,raw);
        return manager.snapshot(ModDisposals.DISPOSAL_TYPE);
    }
    private static JsonObject json(String id) {
        var stream=Objects.requireNonNull(DisposalFixtureTourResolutionTest.class.getResourceAsStream("/data/moonstation14/moonstation14/disposal_entity/"+id+".json"));
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
