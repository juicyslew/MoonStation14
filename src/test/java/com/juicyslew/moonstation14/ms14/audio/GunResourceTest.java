package com.juicyslew.moonstation14.ms14.audio;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class GunResourceTest {
    @Test void sourceAssetsAndSoundEventArePackaged() throws Exception {
        String root = "assets/moonstation14/textures/ss14/weapons/";
        for (String[] asset : new String[][]{
                {"objects/weapons/guns/revolvers/python.rsi/meta.json", "2e5b1fe4a44f2b8aeb0f08a8be0fd0c6f0245daf313cd8ae5fe903259a64fb84"},
                {"objects/fun/capgun.rsi/meta.json", "033c48f482143bf71d56fff56380ed5a782d2296506898b191bd2785846880e5"}}) {
            byte[] bytes = resource(root + asset[0]);
            assertEquals(asset[1], sha(bytes));
            var meta = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(32, meta.getAsJsonObject("size").get("x").getAsInt());
            assertEquals(32, meta.getAsJsonObject("size").get("y").getAsInt());
            String dir = root + asset[0].substring(0, asset[0].lastIndexOf('/') + 1);
            for (var state : meta.getAsJsonArray("states")) {
                String name = state.getAsJsonObject().get("name").getAsString().toLowerCase(java.util.Locale.ROOT);
                try (var image = getClass().getClassLoader().getResourceAsStream(dir + name + ".png")) {
                    assertNotNull(image, name);
                    var decoded = ImageIO.read(image);
                    assertNotNull(decoded, name);
                    assertEquals(0, decoded.getWidth() % 32);
                    assertEquals(0, decoded.getHeight() % 32);
                }
            }
        }
        assertEquals("f77277216bbc595904e3375cf097b8432314e935f0ce0d8c78006aadc88a33cd",
                sha(resource("assets/moonstation14/sounds/ss14/weapons/guns/gunshots/revolver.ogg")));
        assertEquals("a47dd0ce367b04651e23d1dc5e67867326791bf02477960caf1a01bee36912df",
                sha(resource("assets/moonstation14/sounds/ss14/weapons/guns/empty/empty.ogg")));
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var event = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("ss14/weapons/guns/gunshots/revolver");
            assertNotNull(event);
            assertEquals("moonstation14:ss14/weapons/guns/gunshots/revolver", event.getAsJsonArray("sounds").get(0).getAsString());
            var empty = JsonParser.parseString(new String(resource("assets/moonstation14/sounds.json"), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("ss14/weapons/guns/empty");
            assertNotNull(empty);
            assertEquals("moonstation14:ss14/weapons/guns/empty/empty", empty.getAsJsonArray("sounds").get(0).getAsString());
        }
    }

    @Test void revolverAmmoCounterIconsArePackagedAtNativeResourceLocations() throws Exception {
        String base = "ss14/interface/itemstatus/bullets/";
        for (String[] icon : new String[][]{
                {"normal", "7615311a8f9bf12872631099a48a5b3a728231ef376e2eb628d76751acd758a4", "5", "12"},
                {"small", "b71f8baa12d0a05d3c161c53fb2228cfa55f20c235a0f130590dd6f944fb2354", "4", "12"},
                {"tiny", "81decf241b1ed42f499a30e971d65f94ec34c4cec7a33221815ed8a40c84c316", "2", "12"},
                {"empty", "92d79fed512b78b619111dafe139ab4b2a732e7ddf3f1f9c4c97cb6ad7f106eb", "5", "12"}}) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", base + icon[0]);
            assertEquals("moonstation14:" + base + icon[0], id.toString());
            byte[] png = resource("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
            assertEquals(icon[1], sha(png));
            var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            assertNotNull(image);
            assertEquals(Integer.parseInt(icon[2]), image.getWidth());
            assertEquals(Integer.parseInt(icon[3]), image.getHeight());
        }
        String attribution = new String(resource("assets/moonstation14/textures/" + base + "ATTRIBUTION.txt"), StandardCharsets.UTF_8);
        assertTrue(attribution.contains("c9df5ef5d675b0d1d226828bddf6b78c28502d91"));
        assertTrue(attribution.contains("not relicensed"));
    }

    @Test void revolverActionSoundsAreImportedAndMapped() throws Exception {
        assertSound("ss14/weapons/guns/magout/revolver_magout", "ss14/weapons/guns/magout/revolver_magout",
                "c87b4a5115e46fb90bda415a2bb5258ec880cd5cc717e6c1785b2da94ddaa75c");
        assertSound("ss14/weapons/guns/magin/revolver_magin", "ss14/weapons/guns/magin/revolver_magin",
                "05ac8ef3a1219e504b36ecb902df3745301072db487a32d2cef8e62991cb17a2");
        assertSound("ss14/weapons/guns/misc/revolver_spin", "ss14/weapons/guns/misc/revolver_spin",
                "980e4998fab2dfda930582f759aa3d398eb41c49979ffe646c2e55c115fc39d5");
    }

    @Test void bulletHitAndMissSoundsArePackagedAndMapped() throws Exception {
        assertSound("ss14/weapons/guns/hits/bullet_hit", "ss14/weapons/guns/hits/bullet_hit",
                "979954a8c41f6d33c9e5ae45edd09c380600620174decc0fa11cbeededcb6039");
        String eventPath = "ss14/weapons/guns/miss/bullet_miss";
        var eventId = ResourceLocation.fromNamespaceAndPath("moonstation14", eventPath);
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var event = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(eventPath);
            assertNotNull(event);
            assertEquals(4, event.getAsJsonArray("sounds").size());
            String[] hashes = {
                    "d4d4c31021f5b4adf6519c0ab22d3852a427835f6a4f1884c205ce47f1f6eb36",
                    "fb7f0378404d8e1221e7c40fcd2099f425e18c86a056860b9b768e112653e353",
                    "448e920dccefa54c9c6028de3f3a58dbc369f58d363162e30158f01476d5b102",
                    "ce572020da868d4241a07bd3336e952f305934c82c574d7f16c2577d05cfd092"};
            for (int i=0;i<4;i++) {
                String sample = "ss14/weapons/guns/miss/bullet_miss" + (i+1);
                ResourceLocation sampleId = ResourceLocation.fromNamespaceAndPath("moonstation14", sample);
                assertEquals("moonstation14:" + sampleId.getPath(), event.getAsJsonArray("sounds").get(i).getAsString());
                assertEquals(hashes[i], sha(resource("assets/moonstation14/sounds/" + sample + ".ogg")));
            }
            assertEquals(eventPath, eventId.getPath());
        }
    }

    @Test void impactBulletSourceSpriteIsPackaged() throws Exception {
        String root = "assets/moonstation14/textures/ss14/weapons/objects/weapons/guns/projectiles/projectiles_tg.rsi/";
        byte[] metaBytes = resource(root + "meta.json");
        assertEquals("7998553def98b69716024eac33fdd7d058c825c2502227965578628a452b71ec", sha(metaBytes));
        var meta = JsonParser.parseString(new String(metaBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString());
        var impact = java.util.stream.StreamSupport.stream(meta.getAsJsonArray("states").spliterator(), false)
                .map(e -> e.getAsJsonObject()).filter(e -> e.get("name").getAsString().equals("impact_bullet")).findFirst().orElseThrow();
        assertEquals(5, impact.getAsJsonArray("delays").get(0).getAsJsonArray().size());
        assertEquals("b0ac0921d3839ac582d3ab62160229a1d71c0fbd5e6ae0bacd5a840b6cedf9f3", sha(resource(root + "impact_bullet.png")));
        var image = ImageIO.read(new java.io.ByteArrayInputStream(resource(root + "impact_bullet.png")));
        assertEquals(64, image.getWidth());
        assertEquals(96, image.getHeight());
    }

    private void assertSound(String eventPath, String samplePath, String hash) throws Exception {
        ResourceLocation eventId = ResourceLocation.fromNamespaceAndPath("moonstation14", eventPath);
        assertEquals(eventPath, eventId.getPath());
        String asset = "assets/moonstation14/sounds/" + samplePath + ".ogg";
        assertEquals(hash, sha(resource(asset)));
        ResourceLocation sampleId = ResourceLocation.fromNamespaceAndPath("moonstation14", samplePath);
        assertEquals("assets/" + sampleId.getNamespace() + "/sounds/" + sampleId.getPath() + ".ogg", asset);
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
            var sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            var event = sounds.getAsJsonObject(eventId.getPath());
            assertNotNull(event, eventPath);
            assertEquals("moonstation14:" + sampleId.getPath(), event.getAsJsonArray("sounds").get(0).getAsString());
        }
    }

    @Test void registeredGunItemModelsResolveAuthenticIcons() throws Exception {
        assertIconModel("weapon_revolver_python", "moonstation14:ss14/weapons/objects/weapons/guns/revolvers/python.rsi/icon");
        assertIconModel("revolver_cap_gun", "moonstation14:ss14/weapons/objects/fun/capgun.rsi/icon");
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/models/item/revolver_cap_gun_fake.json")) {
            var fake = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertModelParent(fake.get("parent").getAsString(), "moonstation14:item/revolver_cap_gun");
        }
        assertTextureResource("moonstation14:ss14/weapons/objects/weapons/guns/revolvers/python.rsi/icon", "moonstation14:ss14/weapons/objects/weapons/guns/revolvers/python.rsi/icon");
        assertTextureResource("moonstation14:ss14/weapons/objects/fun/capgun.rsi/icon", "moonstation14:ss14/weapons/objects/fun/capgun.rsi/icon");
    }

    @Test void registeredCartridgeModelsResolveSourceLayersAndAttribution() throws Exception {
        String root = "assets/moonstation14/textures/ss14/weapons/objects/weapons/guns/ammunition/casings/";
        assertCartridgeSource(root + "ammo_casing.rsi/meta.json", "76fc6037883997a89ed16b13c4255587348e4bac464e81d9c46265b415909df6",
                new String[]{"base", "tip", "base-spent"});
        assertCartridgeSource(root + "capgun_casing.rsi/meta.json", "92acd56e25b49672d4cb9aa9c11e83ada4fc1cc5c4ad6ad462e321fe89b38cca",
                new String[]{"base", "base-spent"});
        assertCartridgeModel("cartridge_magnum", "moonstation14:ss14/weapons/objects/weapons/guns/ammunition/casings/ammo_casing.rsi/base");
        assertCartridgeModel("cartridge_cap", "moonstation14:ss14/weapons/objects/weapons/guns/ammunition/casings/capgun_casing.rsi/base");
        assertCartridgeModel("cartridge_magnum_ap", "moonstation14:item/cartridge_magnum_ap");
        assertEquals("92a3890b0162a48e3a6467001d4ca24360c7077b7ba5fe7208d12a5f9e945db0",
                sha(resource("assets/moonstation14/textures/item/cartridge_magnum_ap.png")));
    }

    @Test void projectileAndMuzzleSourceRsiResourcesArePackaged() throws Exception {
        assertRsi("objects/weapons/guns/projectiles/projectiles2.rsi/meta.json",
                "8ec353d1938a64fcff62d38d2bda4b5ac75a788d0e76b590b91cecf42e03e83b",
                new String[][]{{"bullet", "f92b8ad7bb71b7e19e7b6e68bbc9d0c22abc40aee954604ead89c98bce58011b", "64", "32"},
                        {"piercing", "05bad94959bed7d9b8490b58239955a4dab557c6c3b9501718d55385af3a6096", "64", "32"}});
        assertRsi("objects/weapons/guns/projectiles/projectiles.rsi/meta.json",
                "bc6f09be68fad3d2ceec50808ddfd3dd27c1a1efead0512fe5529e95d1733a3d",
                new String[][]{{"muzzle_bullet", "23089b784fb600ef035cff2612b3b23b24ef079981580b6fd351c227a1b72626", "32", "32"}});
        assertEquals("5e92455cd10cb3cca6662a49a3023f949b095c99daea90a7dde21b9b50123997",
                sha(resource("assets/moonstation14/textures/ss14/weapons/objects/fun/capgun.rsi/capbullet.png")));
        byte[] capMetaBytes = resource("assets/moonstation14/textures/ss14/weapons/objects/fun/capgun.rsi/meta.json");
        assertEquals("033c48f482143bf71d56fff56380ed5a782d2296506898b191bd2785846880e5", sha(capMetaBytes));
        var capMeta = JsonParser.parseString(new String(capMetaBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CC-BY-NC-SA-3.0", capMeta.get("license").getAsString());
        assertEquals(32, capMeta.getAsJsonObject("size").get("x").getAsInt());
        assertEquals(32, capMeta.getAsJsonObject("size").get("y").getAsInt());
    }

    private void assertRsi(String metaRelativePath, String expectedMetaHash, String[][] expectedStates) throws Exception {
        String prefix = "assets/moonstation14/textures/ss14/weapons/";
        byte[] bytes = resource(prefix + metaRelativePath);
        assertEquals(expectedMetaHash, sha(bytes));
        var meta = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString());
        assertEquals(32, meta.getAsJsonObject("size").get("x").getAsInt());
        assertEquals(32, meta.getAsJsonObject("size").get("y").getAsInt());
        String folder = metaRelativePath.substring(0, metaRelativePath.lastIndexOf('/') + 1);
        for (String[] state : expectedStates) {
            byte[] png = resource(prefix + folder + state[0] + ".png");
            assertEquals(state[1], sha(png));
            var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            assertEquals(Integer.parseInt(state[2]), image.getWidth());
            assertEquals(Integer.parseInt(state[3]), image.getHeight());
            var declaration = java.util.stream.StreamSupport.stream(meta.getAsJsonArray("states").spliterator(), false)
                    .map(e -> e.getAsJsonObject()).filter(e -> e.get("name").getAsString().equals(state[0])).findFirst().orElseThrow();
            if (state[0].equals("bullet") || state[0].equals("piercing"))
                assertEquals(java.util.List.of(0.05, 0.05), java.util.stream.StreamSupport.stream(declaration.getAsJsonArray("delays").get(0).getAsJsonArray().spliterator(), false).map(e -> e.getAsDouble()).toList());
        }
    }

    private void assertCartridgeSource(String metaPath, String hash, String[] states) throws Exception {
        byte[] bytes = resource(metaPath);
        assertEquals(hash, sha(bytes));
        var meta = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("CC-BY-SA-3.0", meta.get("license").getAsString());
        assertTrue(meta.get("copyright").getAsString().contains("vgstation13"));
        assertEquals(32, meta.getAsJsonObject("size").get("x").getAsInt());
        assertEquals(32, meta.getAsJsonObject("size").get("y").getAsInt());
        assertEquals(java.util.List.of(states), java.util.stream.StreamSupport.stream(meta.getAsJsonArray("states").spliterator(), false)
                .map(e -> e.getAsJsonObject().get("name").getAsString()).toList());
        String dir = metaPath.substring(0, metaPath.lastIndexOf('/') + 1);
        for (String state : states) {
            var image = ImageIO.read(getClass().getClassLoader().getResourceAsStream(dir + state.toLowerCase(java.util.Locale.ROOT) + ".png"));
            assertNotNull(image);
            assertTrue(image.getWidth() >= 32 && image.getHeight() >= 32);
        }
    }

    private void assertCartridgeModel(String item, String texture) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/models/item/" + item + ".json")) {
            var model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertModelParent(model.get("parent").getAsString(), model.get("parent").getAsString());
            assertTextureResource(model.getAsJsonObject("textures").get("layer0").getAsString(), texture);
        }
    }

    private void assertIconModel(String item, String expectedTexture) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/models/item/" + item + ".json")) {
            var model = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertModelParent(model.get("parent").getAsString(), model.get("parent").getAsString());
            assertTextureResource(model.getAsJsonObject("textures").get("layer0").getAsString(), expectedTexture);
        }
    }

    private void assertModelParent(String actual, String expected) {
        assertEquals(expected, actual);
        assertEquals(actual, ResourceLocation.parse(actual).toString());
    }

    private void assertTextureResource(String actual, String expected) throws Exception {
        assertEquals(expected, actual);
        ResourceLocation id = ResourceLocation.parse(actual);
        assertEquals(actual, id.toString());
        assertIcon("assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
    }

    private void assertIcon(String classpathPath) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(classpathPath)) {
            assertNotNull(in, classpathPath);
            var image = ImageIO.read(in);
            assertNotNull(image);
            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
        }
    }

    private byte[] resource(String name) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }
    private String sha(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
