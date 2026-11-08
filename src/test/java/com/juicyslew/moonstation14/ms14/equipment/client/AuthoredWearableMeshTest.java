package com.juicyslew.moonstation14.ms14.equipment.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredWearableMeshTest {
    private static final Path ASSETS=findAssets();

    private static Path findAssets() {
        Path current=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(current!=null) {
            Path candidate=current.resolve("src/main/resources/assets/moonstation14/wearables/meshes");
            if(Files.isDirectory(candidate)) return candidate;
            current=current.getParent();
        }
        return Path.of("src/main/resources/assets/moonstation14/wearables/meshes");
    }

    @Test void allInstalledMeshesParseAndRemainImmutable() throws Exception {
        try(var paths=Files.list(ASSETS)) {
            var files=paths.filter(p->p.toString().endsWith(".json")).toList();
            assertTrue(files.size() >= 11);
            for(Path file:files) try(var reader=Files.newBufferedReader(file)) {
                var mesh=AuthoredWearableMesh.parse(reader);
                assertNotNull(mesh.texture()); assertFalse(mesh.bones().isEmpty());
                mesh.bones().values().forEach(quads->quads.forEach(q->{
                    assertEquals(4,q.vertices().size());
                    assertTrue(Float.isFinite(q.normal().x())&&Float.isFinite(q.normal().y())&&Float.isFinite(q.normal().z()));
                    q.vertices().forEach(v->{assertTrue(Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z())); assertTrue(v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1);});
                }));
                assertThrows(UnsupportedOperationException.class,()->mesh.bones().clear());
            }
            var suitFile=ASSETS.resolve("outerclothing/suit_rad.json");
            try(var reader=Files.newBufferedReader(suitFile)) {
                var suit=AuthoredWearableMesh.parse(reader);
                assertEquals(ResourceLocation.parse("moonstation14:textures/entity/equipment/outerclothing/suit_rad.png"),suit.texture());
                assertTrue(Files.isRegularFile(ASSETS.getParent().getParent().resolve("textures/entity/equipment/outerclothing/suit_rad.png")));
                assertTrue(suit.bones().keySet().containsAll(EnumSet.of(EquipmentRenderPlan.Anchor.TORSO,
                        EquipmentRenderPlan.Anchor.LEFT_ARM, EquipmentRenderPlan.Anchor.RIGHT_ARM,
                        EquipmentRenderPlan.Anchor.LEFT_LEG, EquipmentRenderPlan.Anchor.RIGHT_LEG)));
            }
        }
    }

    @Test void approvedBasicArmorAssetsResolveAndSelectAlphaAwareHelmetRoute() throws Exception {
        var catalog=resolvedEquipmentCatalog();
        var helmet=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("basic_helmet"));
        var vest=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("armor_vest"));
        var helmetModel=helmet.clothing().orElseThrow().wornModel().orElseThrow();
        var vestModel=vest.clothing().orElseThrow().wornModel().orElseThrow();
        assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/hats/helmet_basic.json"),helmetModel.mesh());
        assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/outerclothing/armor_basic.json"),vestModel.mesh());
        assertEquals(.9f,helmet.armor().orElseThrow().coefficients().get("blunt"));
        assertEquals(.7f,vest.armor().orElseThrow().coefficients().get("blunt"));
        var helmetMesh=parseAsset("hats/helmet_basic.json");
        var vestMesh=parseAsset("outerclothing/armor_basic.json");
        assertEquals(AuthoredWearableMesh.Material.TRANSLUCENT,helmetMesh.material());
        assertEquals(AuthoredWearableMesh.Material.CUTOUT,vestMesh.material());
        var assetRoot=ASSETS.getParent().getParent();
        for(var mesh:List.of(helmetMesh,vestMesh)) {
            var texture=assetRoot.resolve(mesh.texture().getPath());
            assertTrue(Files.isRegularFile(texture));
            assertNotNull(javax.imageio.ImageIO.read(texture.toFile()));
            assertFalse(mesh.bones().isEmpty());
        }
    }

    @Test void scalesPositionsOnceAndKeepsTopLeftUv() {
        String json="""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:textures/test.png","anchors":{"left_arm":{"canonical_pivot":[9,8,7],"cubes":[{"quads":[{"vertices":[[16,0,0],[0,0,0],[0,16,0],[16,16,0]],"normal":[0,0,-1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """;
        var mesh=AuthoredWearableMesh.parse(new StringReader(json)); var quad=mesh.bones().get(EquipmentRenderPlan.Anchor.LEFT_ARM).get(0);
        assertEquals(1,quad.vertices().get(0).x()); assertEquals(0,quad.vertices().get(0).u());
        assertEquals(ResourceLocation.parse("moonstation14:textures/test.png"),mesh.texture());
    }

    @Test void renderTypeUsesOnlyExactApprovedContracts() throws Exception {
        assertEquals(AuthoredWearableMesh.Material.CUTOUT, parseAsset("outerclothing/armor_basic.json").material());
        assertEquals(AuthoredWearableMesh.Material.TRANSLUCENT, parseAsset("hats/helmet_basic.json").material());
        assertEquals(AuthoredWearableMesh.Material.CUTOUT, parseAsset("hats/helmet_basic_shell.json").material());
        assertEquals(AuthoredWearableMesh.Material.TRANSLUCENT, parseAsset("hats/helmet_basic_visor.json").material());
        String base = "{\"schema\":\"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)\",\"texture\":\"moonstation14:test\",\"render_type\":\"%s\",\"anchors\":{\"head\":{\"cubes\":[]}}}";
        for (String invalid : List.of("entityCutoutNoCull", "entityCutoutNoCull typo", "prefix entityTranslucent"))
            assertThrows(IllegalArgumentException.class, () -> AuthoredWearableMesh.parse(new StringReader(base.formatted(invalid))));
        assertEquals(AuthoredWearableMesh.Material.CUTOUT, AuthoredWearableMesh.parse(new StringReader(
                "{\"schema\":\"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)\",\"texture\":\"moonstation14:test\",\"anchors\":{\"head\":{\"cubes\":[]}}}")).material());
    }

    @Test void evaHelmetAndBreathingMaskMeshesHaveDistinctHeadAndNeckGeometry() throws Exception {
        var assetRoot=ASSETS.getParent().getParent();
        var helmet=parseAsset("eva_helmet.json");
        var raised=parseAsset("breathing_mask.json");
        var lowered=parseAsset("breathing_mask_lowered.json");
        assertSourceEnvelope(helmet,SOURCE_ENVELOPES.get("eva_helmet"));
        assertSourceEnvelope(raised,SOURCE_ENVELOPES.get("breathing_mask"));
        assertSourceEnvelope(lowered,SOURCE_ENVELOPES.get("breathing_mask_lowered"));
        assertEquals(Set.of(EquipmentRenderPlan.Anchor.HEAD),helmet.bones().keySet());
        assertEquals(Set.of(EquipmentRenderPlan.Anchor.HEAD),raised.bones().keySet());
        assertEquals(Set.of(EquipmentRenderPlan.Anchor.TORSO),lowered.bones().keySet());
        assertTrue(helmet.bones().get(EquipmentRenderPlan.Anchor.HEAD).size()>0);
        assertTrue(raised.bones().get(EquipmentRenderPlan.Anchor.HEAD).size()>0);
        assertTrue(lowered.bones().get(EquipmentRenderPlan.Anchor.TORSO).size()>0);
        for (var mesh : List.of(helmet,raised,lowered)) {
            assertTrue(Files.isRegularFile(assetRoot.resolve(mesh.texture().getPath())));
            for (var quad : mesh.bones().values().stream().flatMap(Collection::stream).toList()) {
                for (var vertex : quad.vertices()) {
                    assertTrue(vertex.u() >= 0 && vertex.u() <= 1 && vertex.v() >= 0 && vertex.v() <= 1);
                    assertTrue(Float.isFinite(vertex.x()) && Float.isFinite(vertex.y()) && Float.isFinite(vertex.z()));
                }
            }
        }
        var catalog=resolvedEquipmentCatalog();
        var mask=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("breathing_mask"));
        assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/breathing_mask_lowered.json"),
                mask.breathMask().orElseThrow().loweredWornModel().orElseThrow().mesh());
        var helmetData=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("eva_helmet"));
        assertEquals("Normal",helmetData.item().size());
        assertEquals(40,helmetData.clothing().orElseThrow().equipDelay());
        assertEquals(40,helmetData.clothing().orElseThrow().unequipDelay());
        assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/eva_helmet.json"),
                helmetData.clothing().orElseThrow().wornModel().orElseThrow().mesh());
    }

    @Test void evaSuitAndTanksResolveMeshTexturesIconsAndBoundedArticulatedGeometry() throws Exception {
        var assetRoot=ASSETS.getParent().getParent();
        var cases=Map.of(
                "eva_suit",Set.of(EquipmentRenderPlan.Anchor.TORSO,EquipmentRenderPlan.Anchor.LEFT_ARM,EquipmentRenderPlan.Anchor.RIGHT_ARM,EquipmentRenderPlan.Anchor.LEFT_LEG,EquipmentRenderPlan.Anchor.RIGHT_LEG),
                "oxygen_tank",Set.of(EquipmentRenderPlan.Anchor.TORSO),
                "emergency_oxygen_tank",Set.of(EquipmentRenderPlan.Anchor.TORSO));
        var catalog=resolvedEquipmentCatalog();
        for(var entry:cases.entrySet()) {
            String id=entry.getKey(); var mesh=parseAsset(id+".json");
            assertEquals(entry.getValue(),mesh.bones().keySet());
            // Frozen from accepted source_family_r1 exports (binding_contract.json): bone-local pixels,
            // converted once by the production parser. A one-pixel tolerance accommodates exporter rounding.
            assertSourceEnvelope(mesh, SOURCE_ENVELOPES.get(id));
            var texture=assetRoot.resolve(mesh.texture().getPath());
            assertTrue(Files.isRegularFile(texture));
            var image=javax.imageio.ImageIO.read(texture.toFile()); assertNotNull(image); assertTrue(image.getWidth()>0&&image.getHeight()>0);
            var wearableAttribution=Files.readString(Path.of(texture+".attribution.txt"));
            assertTrue(wearableAttribution.contains("CC-BY-SA-3.0")&&wearableAttribution.contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
            var profile=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                    com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id(id));
            assertEquals(ResourceLocation.parse("moonstation14:wearables/meshes/"+id+".json"),
                    profile.clothing().orElseThrow().wornModel().orElseThrow().mesh());
            var itemModel=com.google.gson.JsonParser.parseString(Files.readString(assetRoot.resolve("models/item/"+id+".json"))).getAsJsonObject();
            assertEquals("moonstation14:item/"+id,itemModel.getAsJsonObject("textures").get("layer0").getAsString());
            var icon=javax.imageio.ImageIO.read(assetRoot.resolve("textures/item/"+id+".png").toFile());
            assertNotNull(icon); assertEquals(32,icon.getWidth()); assertEquals(32,icon.getHeight());
            var iconAttribution=Files.readString(assetRoot.resolve("textures/item/"+id+".png.attribution.txt"));
            assertTrue(iconAttribution.contains("CC-BY-SA-3.0")&&iconAttribution.contains("Copyright:"));
        }
        com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.bootstrapClientBundled();
        var templates=com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog();
        var template=com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.require(templates,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.HUMAN_ID);
        var suit=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("eva_suit"));
        var oxygen=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("oxygen_tank"));
        var emergency=com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.require(catalog,
                com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("emergency_oxygen_tank"));
        assertEquals(List.of(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.BACK,
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.SUITSTORAGE),
                oxygen.clothing().orElseThrow().slotFlags());
        assertEquals(List.of(com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.POCKET,
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.BELT,
                        com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.SlotFlag.SUITSTORAGE),
                emergency.clothing().orElseThrow().slotFlags());
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(template,"back",oxygen,Set.of(),false));
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(template,"belt",emergency,Set.of(),false));
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(template,"pocket1",emergency,Set.of("jumpsuit"),false));
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(template,"suitstorage",oxygen,Set.of("outerClothing"),true));
        assertTrue(com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(template,"suitstorage",emergency,Set.of("outerClothing"),true));
        assertTrue(suit.allowSuitStorage().isPresent());
    }

    private record Envelope(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    private static final Map<String,Envelope> SOURCE_ENVELOPES=Map.of(
            "eva_helmet",new Envelope(-5.10,-9.70,-5.30,5.55,0,5.10),
            "eva_suit",new Envelope(-4.22,-2.18,-3.15,4.22,12.15,2.50),
            "breathing_mask",new Envelope(-4.16,-2.95,-4.99,4.16,-1.05,4.20),
            "breathing_mask_lowered",new Envelope(-2.00,-0.02,-3.49,2.00,3.90,-2.15),
            "oxygen_tank",new Envelope(-3.90,-0.05,-2.65,4.10,11.02,6.37),
            "emergency_oxygen_tank",new Envelope(1.68,6.37,-4.31,3.42,11.30,-2.40));

    private static void assertSourceEnvelope(AuthoredWearableMesh mesh,Envelope expected) {
        var vertices=mesh.bones().values().stream().flatMap(Collection::stream).flatMap(q->q.vertices().stream()).toList();
        assertFalse(vertices.isEmpty());
        double[] actual={vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::x).min().orElseThrow(),
                vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::y).min().orElseThrow(),
                vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::z).min().orElseThrow(),
                vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::x).max().orElseThrow(),
                vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::y).max().orElseThrow(),
                vertices.stream().mapToDouble(AuthoredWearableMesh.Vertex::z).max().orElseThrow()};
        // binding_contract.json defines these envelopes as bone-local pixels; parse() divides by 16 once.
        double[] source={expected.minX/16,expected.minY/16,expected.minZ/16,expected.maxX/16,expected.maxY/16,expected.maxZ/16};
        for(int i=0;i<actual.length;i++) assertEquals(source[i],actual[i],1.0/16.0+1e-6,"approved source envelope axis "+i);
    }

    @Test void sourceEnvelopeRejectsGrosslyDisplacedGeometry() {
        String json="""
                {"schema":"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)","texture":"moonstation14:test","anchors":{"head":{"cubes":[{"quads":[{"vertices":[[1000,0,0],[1001,0,0],[1001,1,0],[1000,1,0]],"normal":[0,0,1],"uv_top_left":[[0,0],[1,0],[1,1],[0,1]],"winding":[0,1,2,3]}]}]}}}
                """;
        var displaced=AuthoredWearableMesh.parse(new StringReader(json));
        assertThrows(AssertionError.class,()->assertSourceEnvelope(displaced,SOURCE_ENVELOPES.get("eva_helmet")));
    }

    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData> resolvedEquipmentCatalog() {
        com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.bootstrapClientBundled();
        return com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog();
    }

    private static void assertCubeNames(String file,String anchor,Set<String> expected) throws Exception {
        com.google.gson.JsonObject root;
        try(var reader=Files.newBufferedReader(ASSETS.resolve(file))) { root=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(); }
        var cubes=root.getAsJsonObject("anchors").getAsJsonObject(anchor).getAsJsonArray("cubes");
        var actual=new HashSet<String>();
        for(var cube:cubes) actual.add(cube.getAsJsonObject().get("name").getAsString());
        assertTrue(actual.containsAll(expected),file+"/"+anchor+" missing semantic parts: "+expected+" from "+actual);
        for(var cube:cubes) assertEquals(6,cube.getAsJsonObject().getAsJsonArray("quads").size(),
                file+"/"+anchor+"/"+cube.getAsJsonObject().get("name").getAsString()+" must remain a closed six-face authored part");
    }

    private static AuthoredWearableMesh parseAsset(String file) throws Exception {
        try (var reader=Files.newBufferedReader(ASSETS.resolve(file))) { return AuthoredWearableMesh.parse(reader); }
    }

    private static void assertBounds(AuthoredWearableMesh mesh,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        var vertices=mesh.bones().values().stream().flatMap(Collection::stream).flatMap(q->q.vertices().stream()).toList();
        assertFalse(vertices.isEmpty());
        assertTrue(vertices.stream().allMatch(v->v.x()>=minX/16&&v.x()<=maxX/16&&v.y()>=minY/16&&v.y()<=maxY/16&&v.z()>=minZ/16&&v.z()<=maxZ/16));
    }

    @Test void rejectsUnknownBonesInvalidWindingAndInvalidValues() {
        String base="{\"schema\":\"moonstation14.wearable_interchange.v1 (proposed, NOT existing runtime loader)\",\"texture\":\"moonstation14:test\",\"anchors\":%s}";
        assertThrows(IllegalArgumentException.class,()->AuthoredWearableMesh.parse(new StringReader(base.formatted("{\"neck\":{}}"))));
        String malformed="{\"head\":{\"cubes\":[{\"quads\":[{\"vertices\":[[0,0,0],[1,0,0],[1,1,0],[0,1,0]],\"normal\":[0,0,1],\"uv_top_left\":[[0,0],[1,0],[1,1],[0,1]],\"winding\":[0,0,2,3]}]}]}}";
        assertThrows(IllegalArgumentException.class,()->AuthoredWearableMesh.parse(new StringReader(base.formatted(malformed))));
    }
}
