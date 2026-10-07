package com.juicyslew.moonstation14.ms14.disposal.client;

import com.juicyslew.moonstation14.ms14.disposal.network.DisposalTubeVisualPayload;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalTubeVisualTest {
    @Test void mapsImportedFramesAndRoutingCardinals() {
        assertEquals("moonstation14:textures/block/disposal/pipe-s_frame",DisposalTubePresentation.texture("disposal_pipe").toString());
        assertEquals(DisposalTubePresentation.texture("disposal_pipe"),DisposalTubePresentation.texture("moonstation14:disposal_pipe"));
        assertEquals("moonstation14:textures/block/disposal/pipe-y_frame",DisposalTubePresentation.texture("moonstation14:disposal_y_junction").toString());
        assertNull(DisposalTubePresentation.texture("not_supported"));
        assertEquals(0,DisposalTubePresentation.frame(Direction.NORTH));assertEquals(1,DisposalTubePresentation.frame(Direction.EAST));
        assertEquals(2,DisposalTubePresentation.frame(Direction.SOUTH));assertEquals(3,DisposalTubePresentation.frame(Direction.WEST));
        assertEquals(0,DisposalTubePresentation.frame(new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.UP,Direction.WEST)));
        for(String prototype:List.of("disposal_pipe","disposal_bend","disposal_trunk","disposal_junction","disposal_junction_flipped","disposal_y_junction","disposal_x_junction","disposal_tagger","disposal_router","disposal_router_flipped")){
            var texture=DisposalTubePresentation.texture(prototype);assertNotNull(texture,prototype);
            for(int frame=0;frame<4;frame++)assertNotNull(DisposalTubeVisualTest.class.getResourceAsStream("/assets/"+texture.getNamespace()+"/"+texture.getPath()+frame+".png"),prototype+" frame "+frame);
        }
    }
    @Test void snapshotRoundTripsAndCacheMergesBatchesAndFiltersByVisibility() {
        var dim=ResourceLocation.withDefaultNamespace("overworld");var pos=new BlockPos(1,64,2);
        var host=UUID.randomUUID();
        var record=new DisposalTubeRecord(UUID.randomUUID(),pos,"disposal_pipe",new com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation(Direction.UP,Direction.WEST),true,"tag","",12,DisposalTubeRecord.HostKind.SEALED_WALL,"wall-host",host);
        var payload=new DisposalTubeVisualPayload(dim,0,0,true,List.of(record));var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { DisposalTubeVisualPayload.STREAM_CODEC.encode(buf,payload);assertEquals(payload,DisposalTubeVisualPayload.STREAM_CODEC.decode(buf)); }
        finally {buf.release();}
        var cache=new DisposalTubeClientCache();assertTrue(cache.replace(dim,0,0,true,List.of(record)));
        assertEquals(record,cache.get(dim,pos).orElseThrow());
        assertTrue(cache.get(ResourceLocation.withDefaultNamespace("the_nether"),pos).isEmpty());
        assertEquals(List.of(record),cache.visible(dim,1.5,64.5,2.5,2));
        assertTrue(cache.visible(dim,1000,64,1000,2).isEmpty());
        assertTrue(cache.replace(dim,0,0,false,List.of()));assertEquals(List.of(record),cache.visible(dim,1.5,64.5,2.5,2));
        cache.remove(dim,0,0);assertTrue(cache.visible(dim,1.5,64.5,2.5,2).isEmpty());
    }
}
