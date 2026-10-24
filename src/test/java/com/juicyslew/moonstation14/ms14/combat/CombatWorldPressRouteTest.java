package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatWorldPressRouteTest {
    @Test void firstConsumerRoutesCombatBeforeContextMenu() {
        assertEquals(CombatWorldPressRoute.Route.SECONDARY, CombatWorldPressRoute.route(true,false,true,false,false,false));
        assertEquals(CombatWorldPressRoute.Route.CONTEXT, CombatWorldPressRoute.route(true,false,false,false,false,false));
        assertEquals(CombatWorldPressRoute.Route.NONE, CombatWorldPressRoute.route(true,true,true,false,false,false));
        assertEquals(CombatWorldPressRoute.Route.NONE, CombatWorldPressRoute.route(false,false,true,false,false,false));
        assertEquals(CombatWorldPressRoute.Route.CONTEXT, CombatWorldPressRoute.route(true,false,true,true,false,false));
        assertEquals(CombatWorldPressRoute.Route.CONTEXT, CombatWorldPressRoute.route(true,false,true,false,true,false));
        assertEquals(CombatWorldPressRoute.Route.CONTEXT, CombatWorldPressRoute.route(true,false,true,false,false,true));
    }
}
