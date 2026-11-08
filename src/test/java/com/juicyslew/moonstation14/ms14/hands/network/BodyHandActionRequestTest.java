package com.juicyslew.moonstation14.ms14.hands.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyHandActionRequestTest {
    @Test void selectHandRequiresNoItemToken() {
        assertDoesNotThrow(() -> new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                "right", 4, 8, 12, null, null));
        assertThrows(IllegalArgumentException.class, () -> new BodyHandActionRequest(
                BodyHandActionRequest.Action.SELECT_HAND, "right", 4, 8, 12, null, "stale-item-token"));
    }

    @Test void useOnHandCodecCarriesTargetHandAndTargetTokenProof() {
        var request = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND,
                "left", 4, 7, 12, null, "source-token", "target-token", -1, -1, -1,
                -1, -1, -1, null, null, "right", null);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(buf, request);
        assertEquals(request, BodyHandActionRequest.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }

    @Test void svalinnCellHandlingSequenceUsesValidRequestProofs() {
        var eject = new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_CELL,
                "right", 1, 2, 10, null, "gun-token");
        var selectCell = new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                "left", 1, 3, 11, null, null);
        var dropCell = new BodyHandActionRequest(BodyHandActionRequest.Action.DROP,
                "left", 1, 4, 12, null, "cell-token");
        var insertCell = new BodyHandActionRequest(BodyHandActionRequest.Action.USE_ON_HAND,
                "left", 1, 5, 13, null, "spare-token", "gun-token", -1, -1, -1,
                -1, -1, -1, null, null, "right", null);
        var selectGun = new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                "right", 1, 6, 14, null, null);

        assertEquals(2, eject.sequence());
        assertEquals(3, selectCell.sequence());
        assertEquals(4, dropCell.sequence());
        assertEquals(5, insertCell.sequence());
        assertEquals(6, selectGun.sequence());
        assertNull(selectCell.expectedToken());
        assertNull(selectGun.expectedToken());
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(buf, selectGun);
        assertEquals(selectGun, BodyHandActionRequest.STREAM_CODEC.decode(buf));
        assertFalse(buf.isReadable());
    }

    @Test void dualSourceGunControlsEncodeHeldAndWorldFormsWithOptionalActorProof(){
        var target=java.util.UUID.randomUUID();
        for(var action:java.util.List.of(BodyHandActionRequest.Action.GUN_CYCLE_FIRE_MODE,
                BodyHandActionRequest.Action.GUN_EJECT_MAGAZINE,BodyHandActionRequest.Action.GUN_TOGGLE_BOLT,
                BodyHandActionRequest.Action.GUN_EJECT_CELL,BodyHandActionRequest.Action.GUN_EMPTY_CYLINDER,
                BodyHandActionRequest.Action.GUN_SPIN_CYLINDER)){
            for(var pair:java.util.List.of(new BodyHandActionRequest(action,"right",2,3,4,null,"held-token"),
                    new BodyHandActionRequest(action,"right",2,3,4,target,null),
                    new BodyHandActionRequest(action,"right",2,3,4,target,"actor-token"))){
                var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
                BodyHandActionRequest.STREAM_CODEC.encode(buf,pair);
                assertEquals(pair,BodyHandActionRequest.STREAM_CODEC.decode(buf),action.name());
                assertFalse(buf.isReadable());
            }
        }
        assertThrows(IllegalArgumentException.class,()->new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_CELL,
                "right",2,3,4,null,null));
        assertThrows(IllegalArgumentException.class,()->new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_CELL,
                "right",2,3,4,target," "));
        assertThrows(IllegalArgumentException.class,()->new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EMPTY,
                "right",2,3,4,target," "));
        assertThrows(IllegalArgumentException.class,()->new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_TOGGLE_WIELD,
                "right",2,3,4,target,"gun-token"));
    }

    @Test void dualSourceGunCodecRejectsMalformedTargetFlagAndTrailingData(){
        var invalid=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        invalid.writeEnum(BodyHandActionRequest.Action.GUN_EJECT_CELL);invalid.writeUtf("right");
        invalid.writeVarLong(2);invalid.writeVarLong(3);invalid.writeVarLong(4);invalid.writeByte(2);
        assertThrows(IllegalArgumentException.class,()->BodyHandActionRequest.STREAM_CODEC.decode(invalid));
        var valid=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        BodyHandActionRequest.STREAM_CODEC.encode(valid,new BodyHandActionRequest(BodyHandActionRequest.Action.GUN_EJECT_CELL,
                "right",2,3,4,null,"held-token"));valid.writeByte(1);
        assertThrows(IllegalArgumentException.class,()->BodyHandActionRequest.STREAM_CODEC.decode(valid));
    }
}
