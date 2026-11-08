package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.singularity.client.FieldFaceVisualCache;
import com.juicyslew.moonstation14.ms14.singularity.containment.client.FieldFaceRenderer;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldFaceSnapshotPayload;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldFaceRecipientSnapshots;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FieldFaceSnapshotPayloadTest {
    @Test void substrateTextureIsPackaged() throws Exception {
        try (var input=getClass().getResourceAsStream("/assets/moonstation14/textures/singularity/field_substrate.png")) {
            assertNotNull(input);
            var image=javax.imageio.ImageIO.read(input);
            assertNotNull(image);
            assertEquals(1,image.getWidth());
            assertEquals(1,image.getHeight());
            int rgba=image.getRGB(0,0);
            assertEquals(255,(rgba >>> 24) & 0xff);
            assertEquals(255,(rgba >>> 16) & 0xff);
            assertEquals(255,(rgba >>> 8) & 0xff);
            assertEquals(255,rgba & 0xff);
        }
    }
    @Test void roundTripsCompleteSnapshot() {
        var payload=new FieldFaceSnapshotPayload(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),List.of(new FieldFaceSnapshotPayload.Face("x:1",Direction.Axis.X,1,2,8,3,9)));
        var buf=buffer();
        try { FieldFaceSnapshotPayload.STREAM_CODEC.encode(buf,payload); assertEquals(payload,FieldFaceSnapshotPayload.STREAM_CODEC.decode(buf)); }
        finally { buf.release(); }
    }
    @Test void cacheReplacesWholeSnapshotAndClearsOnDimensionChange() {
        var dim=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
        var face=new FieldFaceSnapshotPayload.Face("x:1",Direction.Axis.X,1,2,8,3,9);
        FieldFaceVisualCache.accept(new FieldFaceSnapshotPayload(dim,List.of(face)));
        assertEquals(List.of(face),FieldFaceVisualCache.faces());
        FieldFaceVisualCache.accept(new FieldFaceSnapshotPayload(dim,List.of()));
        assertTrue(FieldFaceVisualCache.faces().isEmpty());
        FieldFaceVisualCache.accept(new FieldFaceSnapshotPayload(dim,List.of(face)));
        FieldFaceVisualCache.clearUnless(ResourceLocation.fromNamespaceAndPath("minecraft","the_nether"));
        assertTrue(FieldFaceVisualCache.faces().isEmpty());
    }
    @Test void recipientRelaySendsStableGeometryOnFirstJoinAndReentryAndClearsOnExit() {
        var dim=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
        var id=java.util.UUID.randomUUID();
        var faces=List.of(Direction.values()).stream().map(direction -> new FieldFaceSnapshotPayload.Face(
                "box:"+direction,direction.getAxis(),direction.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0,
                0,1,0,1)).toList();
        var relay=new FieldFaceRecipientSnapshots();
        var first=relay.update(id,true,faces).orElseThrow();
        assertEquals(6,first.size());
        var encoded=buffer();
        try {
            FieldFaceSnapshotPayload.STREAM_CODEC.encode(encoded,new FieldFaceSnapshotPayload(dim,first));
            FieldFaceVisualCache.accept(FieldFaceSnapshotPayload.STREAM_CODEC.decode(encoded));
            assertEquals(faces,FieldFaceVisualCache.faces());
        } finally { encoded.release(); }
        assertTrue(relay.update(id,true,faces).isEmpty());
        assertTrue(relay.update(id,false,faces).orElseThrow().isEmpty());
        FieldFaceVisualCache.accept(new FieldFaceSnapshotPayload(dim,List.of()));
        assertTrue(FieldFaceVisualCache.faces().isEmpty());
        assertEquals(faces,relay.update(id,true,faces).orElseThrow());
        relay.remove(id);
        assertEquals(faces,relay.update(id,true,faces).orElseThrow());
        relay.remove(id); // Dimension change clears old-level sent state; returning observer receives full snapshot.
        assertEquals(faces,relay.update(id,true,faces).orElseThrow());
        // A recipient in another dimension has independent delivery state/cache selection.
        assertEquals(faces,new FieldFaceRecipientSnapshots().update(id,true,faces).orElseThrow());
    }
    @Test void rendererUsesSourceCadenceAndRejectsOtherDimensions() {
        assertEquals(0,FieldFaceRenderer.frameAtSeconds(0));
        assertEquals(1,FieldFaceRenderer.frameAtSeconds(.1));
        assertEquals(15,FieldFaceRenderer.frameAtSeconds(1.5));
        assertEquals(0,FieldFaceRenderer.frameAtSeconds(1.6));
        assertTrue(FieldFaceRenderer.snapshotMatchesDimension(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),ResourceLocation.fromNamespaceAndPath("minecraft","overworld")));
        assertFalse(FieldFaceRenderer.snapshotMatchesDimension(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),ResourceLocation.fromNamespaceAndPath("minecraft","the_nether")));
        assertFalse(FieldFaceRenderer.snapshotMatchesDimension(null,ResourceLocation.fromNamespaceAndPath("minecraft","overworld")));
    }
    @Test void faceTilingCoversEveryCellOnEveryAxisWithoutGaps() {
        for (Direction.Axis axis : Direction.Axis.values()) {
            var face = new FieldFaceSnapshotPayload.Face("face", axis, 7, -2, 2, 4, 7);
            var cells = FieldFaceRenderer.cells(face);
            assertEquals(12, cells.size());
            for (int u=-2;u<2;u++) for(int v=4;v<7;v++) {
                double expectedU=u + .5, expectedV=v + .5;
                assertEquals(1, cells.stream().filter(c -> c.origin()[c.uAxis()]==expectedU
                        && c.origin()[c.vAxis()]==expectedV && c.origin()[axis.ordinal()]==7.5).count());
            }
        }
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY); }
}
