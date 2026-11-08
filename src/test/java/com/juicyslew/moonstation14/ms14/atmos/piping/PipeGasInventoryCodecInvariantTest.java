package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventoryCodecInvariantTest {
    private static final String NODE="{\"x\":0,\"y\":0,\"z\":0,\"layer\":\"PRIMARY\",\"port\":\"\",\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"volume\":2,\"entry_version\":1}";
    private static final String NETWORK="{\"id\":\"00000000-0000-0000-0000-000000000002\",\"generation\":0,\"revision\":0,\"members\":[\"0,0,0,PRIMARY,\"],\"temperature\":293.15,\"gases\":[{\"id\":\"oxygen\",\"moles\":2}]}";
    private static String save(String entries,String retired,String networks) { return "{\"version\":2,\"revision\":0,\"entries\":["+entries+"],\"retired_entries\":["+retired+"],\"networks\":["+networks+"]}"; }
    private static boolean rejects(String json) { return PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).error().isPresent(); }

    @Test void schemaTwoRoundTripEncodesNodeMetadataNetworksAndFiniteArchive() {
        var valid=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(save(NODE,"",NETWORK))).getOrThrow(IllegalStateException::new);
        var json=PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE,valid).getOrThrow(IllegalStateException::new).getAsJsonObject();
        assertEquals(2,json.get("version").getAsInt()); assertTrue(json.has("networks")); assertTrue(json.has("retired_entries"));
        assertFalse(json.getAsJsonArray("entries").get(0).getAsJsonObject().has("gases"));
        assertTrue(PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,json).result().isPresent());
    }

    @Test void malformedNetworkMembershipCountersVolumesAndFiniteGasReject() {
        assertTrue(rejects(save(NODE,"",NETWORK.replace("[\"0,0,0,PRIMARY,\"]","[]"))));
        assertTrue(rejects(save(NODE,"",NETWORK.replace("0,0,0,PRIMARY,","9,0,0,PRIMARY,"))));
        String second=NETWORK.replace("00000000-0000-0000-0000-000000000002","00000000-0000-0000-0000-000000000003");
        assertTrue(rejects(save(NODE,"",NETWORK+","+second)));
        assertTrue(rejects(save(NODE,"",NETWORK.replace("\"revision\":0","\"revision\":-1"))));
        assertTrue(rejects(save(NODE,"",NETWORK.replace("\"temperature\":293.15","\"temperature\":-1"))));
        assertTrue(rejects(save(NODE,"",NETWORK.replace("\"moles\":2","\"moles\":-2"))));
        assertTrue(rejects(save(NODE,"",NETWORK.replace("\"generation\":0","\"generation\":-1"))));
        assertTrue(rejects(save(NODE,"",NETWORK+","+NETWORK)));
        String distinctId=NETWORK.replace("00000000-0000-0000-0000-000000000002","00000000-0000-0000-0000-000000000005");
        assertTrue(rejects(save(NODE,"",NETWORK+","+distinctId)),"duplicate node membership across networks rejected");
        assertTrue(rejects(save(NODE,"", "")));
        assertTrue(rejects(save(NODE.replace("\"volume\":2","\"volume\":0"),"",NETWORK)));
        assertTrue(rejects(save(NODE,"",NETWORK).replace("\"version\":2","\"version\":1")));
    }

    @Test void retiredArchiveRejectsDuplicateOwnersAndPersistsPhysicalVolume() {
        String archive="{\"owner\":{\"x\":4,\"y\":0,\"z\":0,\"layer\":\"PRIMARY\",\"port\":\"\",\"uuid\":\"00000000-0000-0000-0000-000000000004\",\"volume\":3,\"entry_version\":1},\"temperature\":310,\"gases\":[{\"id\":\"oxygen\",\"moles\":1}]}";
        assertTrue(rejects(save(NODE,archive+","+archive,NETWORK)));
        var decoded=PipeGasInventory.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(save(NODE,archive,NETWORK))).getOrThrow(IllegalStateException::new);
        var retired=decoded.retiredSnapshot().values().iterator().next();
        assertEquals(3.0,retired.mixture().volumeCubicMeters(),1e-6); assertEquals(1.0,retired.mixture().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN),1e-6);
    }
}
