package com.juicyslew.moonstation14.ms14.hands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Production dispatch policy for empty-hand machine workspace activation; not server admission evidence. */
class BodyMachineActivationDispatchTest {
    @Test void useAndClientActivateShareMachineWorkspaceAndValveDispatch() {
        for(var intent:new BodyBlockUseRequest.Intent[]{BodyBlockUseRequest.Intent.USE,BodyBlockUseRequest.Intent.ACTIVATE_WORLD}) {
            var request=new BodyBlockUseRequest(1,1,0,"main",null,new BlockPos(1,2,3),Direction.UP,false,intent);
            var calls=new ArrayList<String>();
            var outcome=BodyHandActionNetworking.dispatchMachineActivation(request,
                    ()->{calls.add("alarm");return false;},
                    ()->{calls.add("atmos");return true;},
                    ()->{calls.add("valve");return false;});
            assertEquals(BodyBlockUseResult.Outcome.NO_CHANGE,outcome);
            assertEquals(List.of("alarm","atmos"),calls,"successful workspace open must stop dispatch before valve fallback");
        }
    }

    @Test void nonmachineAndotherIntentsFallThroughWithoutBeingConsumed() {
        for(var intent:new BodyBlockUseRequest.Intent[]{BodyBlockUseRequest.Intent.USE,BodyBlockUseRequest.Intent.ACTIVATE_WORLD}) {
            var request=new BodyBlockUseRequest(1,1,0,"main",null,new BlockPos(1,2,3),Direction.UP,false,intent);
            var calls=new ArrayList<String>();
            assertNull(BodyHandActionNetworking.dispatchMachineActivation(request,
                    ()->{calls.add("alarm");return false;},()->{calls.add("atmos");return false;},
                    ()->{calls.add("valve");return false;}));
            assertEquals(List.of("alarm","atmos","valve"),calls);
        }
        var climb=new BodyBlockUseRequest(1,1,0,"main",null,new BlockPos(1,2,3),Direction.UP,false,BodyBlockUseRequest.Intent.CLIMB_INSIDE);
        assertNull(BodyHandActionNetworking.dispatchMachineActivation(climb,()->{fail("should fall through");return false;},()->false,()->false));
    }

    @Test void directValveActivationRemainsLastMachineFallback() {
        var request=new BodyBlockUseRequest(1,1,0,"main",null,new BlockPos(1,2,3),Direction.UP,false,
                BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        var calls=new ArrayList<String>();
        assertEquals(BodyBlockUseResult.Outcome.NO_CHANGE,BodyHandActionNetworking.dispatchMachineActivation(request,
                ()->{calls.add("alarm");return false;},()->{calls.add("atmos");return false;},()->{calls.add("valve");return true;}));
        assertEquals(List.of("alarm","atmos","valve"),calls);
    }
}
