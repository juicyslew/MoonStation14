package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprintProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.ModItemFootprints;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GunFootprintResourceTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    @Test
    void allRegisteredGunContentFootprintsResolveAndUsePocketFitPolicy() {
        var manager = new PrototypeManager();
        manager.register(ModItemFootprints.TYPE);
        var result = new AtomicReference<Map<ResourceLocation, ItemFootprintProfile>>();
        manager.stage(Map.of(ModItemFootprints.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("item_footprint")), catalogs -> {
            var resolved = new LinkedHashMap<ResourceLocation, ItemFootprintProfile>();
            catalogs.get(ModItemFootprints.TYPE.typeId()).forEach((key, raw) -> resolved.put(key,
                    ItemFootprintProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            result.set(Map.copyOf(resolved));
        });
        var profiles = result.get();
        var guns = List.of("weapon_pistol_mk58", "weapon_rifle_lecter", "weapon_revolver_inspector",
                "weapon_shotgun_kammerer", "weapon_sniper_mosin", "weapon_laser_gun",
                "weapon_energy_shotgun", "weapon_laser_svalinn");
        var accessoriesAndAmmo = List.of("magazine_pistol", "magazine_rifle", "speedloader_magnum",
                "power_cell_small", "cartridge_pistol", "cartridge_rifle", "cartridge_magnum",
                "cartridge_light_rifle", "shell_shotgun", "weapon_capacitor_recharger");
        for (String name : java.util.stream.Stream.concat(guns.stream(), accessoriesAndAmmo.stream()).toList())
            assertNotNull(profiles.get(id(name)), "missing resolved footprint for registered item " + name);

        var expected = Map.ofEntries(
                Map.entry("weapon_pistol_mk58", expected("Small", new int[][]{{0,0},{1,0},{0,1}})),
                Map.entry("weapon_revolver_inspector", expected("Small", new int[][]{{0,0},{1,0},{0,1}})),
                Map.entry("weapon_rifle_lecter", expected("Huge", rectangle(4,3))),
                Map.entry("weapon_shotgun_kammerer", expected("Large", new int[][]{{0,0},{1,0},{2,0},{3,0},{4,0}})),
                Map.entry("weapon_sniper_mosin", expected("Large", rectangle(4,2))),
                Map.entry("weapon_laser_gun", expected("Small", new int[][]{{0,0},{1,0},{0,1}})),
                Map.entry("weapon_energy_shotgun", expected("Large", rectangle(4,2))),
                Map.entry("weapon_laser_svalinn", expected("Small", new int[][]{{0,0},{1,0},{0,1}})),
                Map.entry("weapon_capacitor_recharger", expected("Normal", rectangle(2,2))),
                Map.entry("magazine_pistol", expected("Small", new int[][]{{0,0},{0,1}})),
                Map.entry("magazine_rifle", expected("Small", new int[][]{{0,0},{0,1}})),
                Map.entry("speedloader_magnum", expected("Small", new int[][]{{0,0},{0,1}})),
                Map.entry("power_cell_small", expected("Small", new int[][]{{0,0},{0,1}})),
                Map.entry("cartridge_pistol", expected("Tiny", new int[][]{{0,0}})),
                Map.entry("cartridge_rifle", expected("Tiny", new int[][]{{0,0}})),
                Map.entry("cartridge_magnum_standard", expected("Tiny", new int[][]{{0,0}})),
                Map.entry("cartridge_light_rifle", expected("Tiny", new int[][]{{0,0}})),
                Map.entry("shell_shotgun", expected("Tiny", new int[][]{{0,0}})));
        expected.forEach((name, spec) -> {
            var actual = profiles.get(id(name));
            assertEquals(java.util.Optional.of(ItemFootprintProfile.ItemSize.valueOf(spec.itemSize)), actual.itemSize(), name + " physical item_size");
            assertEquals(spec.cells, Set.copyOf(actual.cells()), name + " exact source cells");
        });

        for (String name : List.of("weapon_rifle_lecter", "weapon_shotgun_kammerer", "weapon_sniper_mosin"))
            assertTrue(profiles.get(id(name)).cells().size() > 1, name + " must not be a one-cell pocket item");

        var pocket = new InventoryTemplateData(List.of(new InventoryTemplateData.Slot("pocket1", "Pocket",
                ResourceLocation.withDefaultNamespace("test"), "Default", 0, 0,
                List.of(InventoryTemplateData.SlotFlag.POCKET), List.of(), List.of(), false)));
        assertTrue(EquipmentFitPolicy.fitsPocket(pocket, "pocket1", profiles.get(id("cartridge_rifle")), 1, 1, Set.of()));
        assertTrue(EquipmentFitPolicy.fitsPocket(pocket, "pocket1", profiles.get(id("weapon_pistol_mk58")), 1, 1, Set.of()));
        assertFalse(EquipmentFitPolicy.fitsPocket(pocket, "pocket1", profiles.get(id("weapon_rifle_lecter")), 1, 1, Set.of()));
        assertFalse(EquipmentFitPolicy.fitsPocket(pocket, "pocket1", profiles.get(id("weapon_sniper_mosin")), 1, 1, Set.of()));
        assertEquals(List.of(new GridGeometry.Cell(0, 0)), profiles.get(id("shell_shotgun")).cells());
    }

    private record Expected(String itemSize, Set<GridGeometry.Cell> cells) { }
    private static Expected expected(String size, int[][] cells) {
        return new Expected(size, java.util.Arrays.stream(cells).map(c -> new GridGeometry.Cell(c[0], c[1])).collect(java.util.stream.Collectors.toSet()));
    }
    private static int[][] rectangle(int width, int height) {
        var result = new int[width * height][2]; int i = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) result[i++] = new int[]{x,y};
        return result;
    }
}
