package com.juicyslew.moonstation14.ms14.ghost_roles.server;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GhostRoleCatalogSnapshotTest {
    @Test void developmentCatalogEntriesAreTeleportableOnlyForOperatorsAndOrdinaryRestrictionsRemain() {
        var developmentOperator = new GhostRolePayloads.Entry(new UUID(1, 1), "moonstation14:bat", "Bat", true, true, true);
        assertTrue(developmentOperator.teleport());
        assertTrue(developmentOperator.takeover());
        assertTrue(developmentOperator.development());
        var ordinary = new GhostRolePayloads.Entry(new UUID(1, 2), "minecraft:fox", "Fox", true, false, false);
        assertTrue(ordinary.teleport());
        assertFalse(ordinary.takeover());
        assertFalse(ordinary.development());
    }

    @Test void boundedSnapshotKeepsOrdinaryRolesBeforeDeterministicDevelopmentOverflow() {
        List<GhostRolePayloads.Entry> entries = new ArrayList<>();
        for (int i = 0; i < GhostRolePayloads.MAX_ENTRIES + 20; i++) {
            boolean development = i != GhostRolePayloads.MAX_ENTRIES + 19;
            entries.add(new GhostRolePayloads.Entry(new UUID(0, i), "role-" + i, "Role", true, true, development));
        }
        var bounded = GhostRoleCatalog.boundSnapshot(entries);
        assertEquals(GhostRolePayloads.MAX_ENTRIES, bounded.size());
        assertTrue(bounded.stream().anyMatch(entry -> !entry.development()), "ordinary role must survive development overflow");
        assertEquals(GhostRolePayloads.MAX_ENTRIES - 1,
                bounded.stream().filter(GhostRolePayloads.Entry::development).count());
        assertEquals(bounded, GhostRoleCatalog.boundSnapshot(new ArrayList<>(entries)));
    }
}
