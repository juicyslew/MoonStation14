package com.juicyslew.moonstation14.ms14.doors.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AirlockQuadClipperTest {
    @Test
    void clipsCrossingFaceAndInterpolatesUvAtDoorBoundary() {
        var result = AirlockQuadClipper.clip(List.of(
                new AirlockQuadClipper.Vertex(-.5f, .25f, .5f, 0, 0),
                new AirlockQuadClipper.Vertex(.5f, .25f, .5f, 1, 0),
                new AirlockQuadClipper.Vertex(.5f, 1.75f, .5f, 1, 1),
                new AirlockQuadClipper.Vertex(-.5f, 1.75f, .5f, 0, 1)));

        assertEquals(4, result.size());
        assertEquals(0, result.get(0).x(), 1e-6);
        assertEquals(.5, result.get(0).u(), 1e-6);
        assertEquals(0, result.get(3).x(), 1e-6);
        assertEquals(.5, result.get(3).u(), 1e-6);
        assertTrue(result.stream().allMatch(v -> v.x() >= 0 && v.x() <= 1 && v.y() >= 0 && v.y() <= 2
                && v.z() >= 0 && v.z() <= 1));
    }

    @Test
    void clipsEachDoorVolumePlaneAndDropsWhollyExternalFaces() {
        assertTrue(AirlockQuadClipper.clip(List.of(v(-1, 0, .5f), v(-.1f, 0, .5f), v(-.1f, 1, .5f), v(-1, 1, .5f))).isEmpty());
        assertTrue(AirlockQuadClipper.clip(List.of(v(1.1f, 0, .5f), v(2, 0, .5f), v(2, 1, .5f), v(1.1f, 1, .5f))).isEmpty());
        assertTrue(AirlockQuadClipper.clip(List.of(v(.2f, -2, .5f), v(.8f, -2, .5f), v(.8f, -1, .5f), v(.2f, -1, .5f))).isEmpty());
        assertTrue(AirlockQuadClipper.clip(List.of(v(.2f, 3, .5f), v(.8f, 3, .5f), v(.8f, 4, .5f), v(.2f, 4, .5f))).isEmpty());
        assertTrue(AirlockQuadClipper.clip(List.of(v(.2f, .5f, -2), v(.8f, .5f, -2), v(.8f, .8f, -1), v(.2f, .8f, -1))).isEmpty());
        assertTrue(AirlockQuadClipper.clip(List.of(v(.2f, .5f, 2), v(.8f, .5f, 2), v(.8f, .8f, 3), v(.2f, .8f, 3))).isEmpty());
        for (var quad : List.of(
                List.of(v(.5f,-1,.5f),v(.8f,-1,.5f),v(.8f,.5f,.5f),v(.5f,.5f,.5f)),
                List.of(v(.5f,1.5f,.5f),v(.8f,1.5f,.5f),v(.8f,2.5f,.5f),v(.5f,2.5f,.5f)),
                List.of(v(.5f,.5f,-1),v(.8f,.5f,-1),v(.8f,.8f,.5f),v(.5f,.8f,.5f)),
                List.of(v(.5f,.5f,.5f),v(1.5f,.5f,.5f),v(1.5f,.8f,.5f),v(.5f,.8f,.5f)))) {
            var clipped = AirlockQuadClipper.clip(quad);
            assertTrue(clipped.size() >= 3);
            assertTrue(clipped.stream().allMatch(v -> v.x() >= 0 && v.x() <= 1 && v.y() >= 0 && v.y() <= 2
                    && v.z() >= 0 && v.z() <= 1));
        }
    }

    @Test
    void boundaryCoincidentEndpointIsDeduplicatedAndZeroAreaPolygonsAreDropped() {
        var clipped = AirlockQuadClipper.clip(List.of(
                new AirlockQuadClipper.Vertex(-.5f, .25f, .5f, 0, 0),
                new AirlockQuadClipper.Vertex(0, .25f, .5f, .5f, 0),
                new AirlockQuadClipper.Vertex(.5f, 1.75f, .5f, 1, 1),
                new AirlockQuadClipper.Vertex(.25f, .5f, .5f, .75f, .25f)));
        assertEquals(4, clipped.size());
        assertEquals(1, clipped.stream().filter(vertex -> vertex.x() == 0 && vertex.y() == .25f
                && vertex.u() == .5f && vertex.v() == 0).count());

        var line = List.of(v(0,.5f,.5f), v(0,.5f,.5f), v(0,1,.5f), v(0,1.5f,.5f));
        assertTrue(AirlockQuadClipper.clip(line).isEmpty());
    }

    private static AirlockQuadClipper.Vertex v(float x,float y,float z) {
        return new AirlockQuadClipper.Vertex(x,y,z,x,y);
    }
}
