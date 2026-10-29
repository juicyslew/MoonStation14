package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class BotanyCatalogItemResourcesTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String SOURCE = ROOT + "textures/ss14/botany/Objects/Specific/Hydroponics/";
    private static final String FOOD_SOURCE = ROOT + "textures/ss14/botany/Objects/Consumable/Food/";

    @Test void itemBindingsAndImportedSpritesAreComplete() throws Exception {
        assertModel("carrot", "moonstation14:item/botany/carrot");
        assertModel("cabbage", "moonstation14:item/botany/cabbage");
        assertModel("carrot_seeds", "moonstation14:item/botany/packet_seed",
                "moonstation14:item/botany/carrot_seed", "moonstation14:item/botany/packet_paper");
        assertModel("cabbage_seeds", "moonstation14:item/botany/packet_seed",
                "moonstation14:item/botany/cabbage_seed", "moonstation14:item/botany/packet_paper");
        for (String name : new String[]{"carrot", "cabbage", "carrot_seed", "cabbage_seed", "packet_seed", "packet_paper"}) {
            try (var stream = resource(ROOT + "textures/item/botany/" + name + ".png")) {
                var image = ImageIO.read(stream);
                assertNotNull(image, name);
                assertEquals(32, image.getWidth(), name);
                assertEquals(32, image.getHeight(), name);
            }
        }
        same("carrot.rsi/produce.png", "carrot.png");
        same("cabbage.rsi/produce.png", "cabbage.png");
        same("carrot.rsi/seed.png", "carrot_seed.png");
        same("cabbage.rsi/seed.png", "cabbage_seed.png");
        var carrot = json(SOURCE + "carrot.rsi/meta.json");
        var cabbage = json(SOURCE + "cabbage.rsi/meta.json");
        var packet = json(SOURCE + "seeds.rsi/meta.json");
        for (var metadata : new com.google.gson.JsonObject[]{carrot, cabbage, packet}) {
            assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString());
            assertFalse(metadata.get("copyright").getAsString().isBlank());
        }
        assertTrue(packet.get("copyright").getAsString().contains("paper layer by Ian321"));
    }

    @Test void fruitFamiliesUseTheirVerifiedProduceAndPacketStates() throws Exception {
        for (String name : new String[]{"banana", "mimana", "lemon", "lemoon", "lime", "orange",
                "extradimensional_orange", "apple", "golden_apple"}) {
            assertModel(name, "moonstation14:item/botany/" + name);
            assertModel(name + "_seeds", "moonstation14:item/botany/packet_seed",
                    "moonstation14:item/botany/" + name + "_seed", "moonstation14:item/botany/packet_paper");
            same(name + ".rsi/produce.png", name + ".png");
            same(name + ".rsi/seed.png", name + "_seed.png");
            var metadata = json(SOURCE + name + ".rsi/meta.json");
            assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString(), name);
            assertFalse(metadata.get("copyright").getAsString().isBlank(), name);
            assertTrue(metadata.getAsJsonArray("states").toString().contains("\"name\":\"produce\""), name);
            if (!name.equals("extradimensional_orange")) {
                try (var stream = resource(ROOT + "textures/item/botany/" + name + ".png")) {
                    var image = ImageIO.read(stream);
                    assertNotNull(image, name);
                    assertEquals(32, image.getWidth(), name);
                    assertEquals(32, image.getHeight(), name);
                }
            }
        }
        var animatedMeta = json(ROOT + "textures/item/botany/extradimensional_orange.png.mcmeta");
        var animation = animatedMeta.getAsJsonObject("animation");
        assertAnimationSchedule(animation, 23, 160, 160);
        try (var stream = resource(ROOT + "textures/item/botany/extradimensional_orange.png")) {
            var image = ImageIO.read(stream);
            assertEquals(160, image.getWidth());
            assertEquals(160, image.getHeight());
        }
    }

    @Test void additionalBotanyItemsMatchResolvedProductAndPacketSprites() throws Exception {
        String[][] families = {
                {"eggplant", "eggplant", "eggplant", "produce"},
                {"eggy", "egg", "eggy", "icon"},
                {"corn", "corn", "corn", "produce"},
                {"onion", "onion", "onion", "produce"},
                {"onionred", "onion_red", "onion_red", "produce"},
                {"pumpkin", "pumpkin", "pumpkin", "produce"},
                {"blue_pumpkin", "blue_pumpkin", "blue_pumpkin", "produce"},
                {"watermelon", "watermelon", "watermelon", "produce"},
                {"holymelon", "holymelon", "holymelon", "produce"},
                {"pineapple", "pineapple", "pineapple", "produce"},
                {"cocoa", "cocoa_pod", "cocoa", "produce"}
        };
        for (String[] family : families) {
            String seed = family[0], product = family[1], sourceRsi = family[2], productState = family[3];
            assertModel(product, "moonstation14:item/botany/" + product);
            assertModel(seed + "_seeds", "moonstation14:item/botany/packet_seed",
                    "moonstation14:item/botany/" + seed + "_seed", "moonstation14:item/botany/packet_paper");
            String sourceRoot = seed.equals("eggy") ? FOOD_SOURCE + "egg.rsi/" : SOURCE + sourceRsi + ".rsi/";
            String sourceFrame = sourceRoot + productState + ".png";
            same(sourceFrame, "textures/item/botany/" + product + ".png");
            if (!seed.equals("eggy")) same(sourceRoot + "seed.png", "textures/item/botany/" + seed + "_seed.png");
            var metadata = json(sourceRoot + "meta.json");
            assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString(), seed);
            assertFalse(metadata.get("copyright").getAsString().isBlank(), seed);
            assertTrue(metadata.getAsJsonArray("states").toString().contains("\"name\":\"" + productState + "\""), seed);
        }
        // Eggy packets use eggy.rsi, while the actual FoodEgg product inherits Objects/Consumable/Food/egg.rsi.
        same(SOURCE + "eggy.rsi/seed.png", "textures/item/botany/eggy_seed.png");
        var eggyPacket = json(SOURCE + "eggy.rsi/meta.json");
        assertEquals("CC-BY-SA-3.0", eggyPacket.get("license").getAsString());
        assertFalse(eggyPacket.get("copyright").getAsString().isBlank());
        var holySource = json(SOURCE + "holymelon.rsi/meta.json");
        com.google.gson.JsonObject produce = null;
        for (var state : holySource.getAsJsonArray("states"))
            if (state.getAsJsonObject().get("name").getAsString().equals("produce")) produce = state.getAsJsonObject();
        assertNotNull(produce);
        assertEquals(4, produce.getAsJsonArray("delays").get(0).getAsJsonArray().size());
        assertEquals(0.3, produce.getAsJsonArray("delays").get(0).getAsJsonArray().get(0).getAsDouble());
        var holyAnimation = json(ROOT + "textures/item/botany/holymelon.png.mcmeta").getAsJsonObject("animation");
        assertEquals(6, holyAnimation.get("frametime").getAsInt());
        assertEquals(4, holyAnimation.getAsJsonArray("frames").size());
        try (var stream = resource(ROOT + "textures/item/botany/holymelon.png")) {
            var image = ImageIO.read(stream);
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
            assertAnimationDimensionsAndIndices(holyAnimation, 64, 64);
        }
        // These named secondary states are present in the imported RSI bundles; avoid copying an already integrated cocoa-bean item.
        for (String[] state : new String[][]{{"corn", "cob"}, {"cocoa", "produce-beans"}, {"onion", "slice"},
                {"onion_red", "slice"}, {"watermelon", "slice"}, {"holymelon", "slice"}, {"pineapple", "slice"},
                {"pumpkin", "carved"}}) {
            var meta = json(SOURCE + state[0] + ".rsi/meta.json");
            assertTrue(meta.getAsJsonArray("states").toString().contains("\"name\":\"" + state[1] + "\""), state[0] + ":" + state[1]);
        }
        for (String[] secondary : new String[][]{
                {"food_corn_trash", "corn.rsi/cob.png"}, {"pineapple_slice", "pineapple.rsi/slice.png"},
                {"onion_slice", "onion.rsi/slice.png"}, {"onion_red_slice", "onion_red.rsi/slice.png"},
                {"watermelon_slice", "watermelon.rsi/slice.png"}, {"holymelon_slice", "holymelon.rsi/slice.png"},
                {"carved_pumpkin", "pumpkin.rsi/carved.png"}}) {
            assertModel(secondary[0], "moonstation14:item/botany/" + secondary[0]);
            same(SOURCE + secondary[1], "textures/item/botany/" + secondary[0] + ".png");
        }
        same(SOURCE + "cocoa.rsi/produce-beans.png", "textures/item/food_cocoa_beans.png");
        for (String[] hat : new String[][]{{"clothing_head_hat_watermelon", "melon"},
                {"clothing_head_hat_holy_watermelon", "holyhatmelon"}}) {
            assertModel(hat[0], "moonstation14:item/botany/" + hat[0]);
            String source = ROOT + "textures/ss14/botany/Clothing/Head/Hats/" + hat[1] + ".rsi/";
            same(source + "icon.png", "textures/item/botany/" + hat[0] + ".png");
            var metadata = json(source + "meta.json");
            assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString());
            assertFalse(metadata.get("copyright").getAsString().isBlank());
        }
        var language = json(ROOT + "lang/en_us.json");
        for (String[] label : new String[][]{{"eggplant", "Eggplant"}, {"egg", "Egg"}, {"corn", "Ear of corn"},
                {"onion_red", "Red onion"}, {"cocoa_pod", "Cocoa pod"}, {"food_corn_trash", "Corn cob"},
                {"pineapple_slice", "Pineapple slice"}, {"carved_pumpkin", "Carved pumpkin"}})
            assertEquals(label[1], language.get("item.moonstation14." + label[0]).getAsString());
    }

    @Test void remainingSupportedSeedFamiliesResolveCanonicalItemsAndSourceSprites() throws Exception {
        String[][] families = {
                {"ambrosia_deus", "ambrosia_deus", "ambrosiadeusseeds", "ambrosia_deus", "ambrosia_deus"},
                {"ambrosia_vulgaris", "ambrosia_vulgaris", "ambrosiavulgarisseeds", "ambrosia_vulgaris", "ambrosia_vulgaris"},
                {"berries", "berries", "berry_seeds", "berries", "berries"},
                {"blood_tomato", "blood_tomato", "blood_tomato_seeds", "blood_tomato", "blood_tomato"},
                {"bloonion", "bloonion", "bloonion_seeds", "bloonion", "bloonion"},
                {"blue_tomato", "blue_tomato", "blue_tomato_seeds", "blue_tomato", "blue_tomato"},
                {"bungo", "bungo", "bungo_seeds", "bungo", "bungo"},
                {"cannabis", "leaves_cannabis", "cannabis_seeds", "cannabis", "cannabis"},
                {"chanterelle", "mushroom", "chanterelleseeds", "chanterelle", "chanterelle"},
                {"cherry", "cherry", "cherry_seeds", "cherry", "cherry"},
                {"chili", "chili", "chiliseeds", "chili", "chili"},
                {"chilly", "chilly", "chillyseeds", "chilly", "chilly"},
                {"cotton", "cotton_bol", "cotton_seeds", "cotton", "cotton"},
                {"death_nettle", "death_nettle", "death_nettle_seeds", "death_nettle", "death_nettle"},
                {"fly_amanita", "fly_amanita", "flyamanitaseeds", "fly_amanita", "fly_amanita"},
                {"galaxythistle", "galaxythistle", "galaxythistleseeds", "galaxythistle", "galaxythistle"},
                {"garlic", "garlic", "garlic_seeds", "garlic", "garlic"},
                {"glasstle", "glasstle", "glasstleseeds", "glasstle", "glasstle"},
                {"grape", "grape", "grape_seeds", "grape", "grape"},
                {"koibean", "food_koibean", "koibean_seeds", "koibean", "koibean"},
                {"laughin_pea", "food_laughin_pea_pod", "laughin_pea_seeds", "laughin_pea", "laughin_pea"},
                {"lily", "lily", "lily_seeds", "lily", "lily"},
                {"lingzhi", "lingzhi", "lingzhiseeds", "lingzhi", "lingzhi"},
                {"nettle", "nettle", "nettle_seeds", "nettle", "nettle"},
                {"oat", "oat_bushel", "oat_seeds", "oat", "oat"},
                {"papercane", "papercane", "papercane_seeds", "papercane", "papercane"},
                {"pea", "food_pea_pod", "pea_seeds", "pea", "pea"},
                {"pyrotton", "pyrotton_bol", "pyrotton_seeds", "pyrotton", "pyrotton"},
                {"rainbow_cannabis", "leaves_cannabis_rainbow", "rainbow_cannabis_seeds", "rainbow_cannabis", "rainbow_cannabis"},
                {"rice", "rice_bushel", "rice_seeds", "rice", "rice"},
                {"soybeans", "food_soybeans", "soybean_seeds", "soybeans", "soybeans"},
                {"steelcap", "steel_log", "steelcap_seeds", "steelcap", "steelcap"},
                {"sugarcane", "sugarcane", "sugarcane_seeds", "sugarcane", "sugarcane"},
                {"tea_plant", "leaves_tea", "tea_plant_seeds", "tea_plant", "tea_plant"},
                {"tobacco", "leaves_tobacco", "tobacco_seeds", "tobacco", "tobacco"},
                {"towercap", "log", "towercap_seeds", "towercap", "towercap"},
                {"world_pea", "food_world_peas", "pea_seeds", "world_pea", "pea"}
        };
        var packets = new java.util.HashSet<String>();
        var language = json(ROOT + "lang/en_us.json");
        for (String[] family : families) {
            String product = family[1], packet = family[2], productRsi = family[3], packetRsi = family[4];
            assertModel(product, "moonstation14:item/botany/" + product);
            same(SOURCE + productRsi + ".rsi/produce.png", "textures/item/botany/" + product + ".png");
            var sourceMeta = json(SOURCE + productRsi + ".rsi/meta.json");
            assertFalse(sourceMeta.get("license").getAsString().isBlank(), productRsi);
            assertFalse(sourceMeta.get("copyright").getAsString().isBlank(), productRsi);
            assertTrue(sourceMeta.getAsJsonArray("states").toString().contains("\"name\":\"produce\""), productRsi);
            assertNotNull(language.get("item.moonstation14." + product), product);
            if (packets.add(packet)) {
                assertModel(packet, "moonstation14:item/botany/packet_seed",
                        "moonstation14:item/botany/" + packet + "_seed", "moonstation14:item/botany/packet_paper");
                same(SOURCE + packetRsi + ".rsi/seed.png", "textures/item/botany/" + packet + "_seed.png");
                var packetMeta = json(SOURCE + packetRsi + ".rsi/meta.json");
                assertFalse(packetMeta.get("license").getAsString().isBlank(), packetRsi);
                assertFalse(packetMeta.get("copyright").getAsString().isBlank(), packetRsi);
                assertNotNull(language.get("item.moonstation14." + packet), packet);
            }
        }
        assertEquals(36, packets.size());
        assertTrue(packets.contains("pea_seeds"));
        // LeavesCannabisRainbow explicitly selects its unshaded produce layer; retain the real 22-frame atlas animation.
        var rainbowMeta = json(SOURCE + "rainbow_cannabis.rsi/meta.json");
        com.google.gson.JsonObject rainbowProduce = null;
        for (var state : rainbowMeta.getAsJsonArray("states"))
            if (state.getAsJsonObject().get("name").getAsString().equals("produce")) rainbowProduce = state.getAsJsonObject();
        assertNotNull(rainbowProduce);
        assertEquals(22, rainbowProduce.getAsJsonArray("delays").get(0).getAsJsonArray().size());
        var rainbowAnimation = json(ROOT + "textures/item/botany/leaves_cannabis_rainbow.png.mcmeta").getAsJsonObject("animation");
        assertAnimationSchedule(rainbowAnimation, 22, 160, 160);
        try (var stream = resource(ROOT + "textures/item/botany/leaves_cannabis_rainbow.png")) {
            var image = ImageIO.read(stream);
            assertEquals(160, image.getWidth());
            assertEquals(160, image.getHeight());
        }
        for (String[] pit : new String[][]{{"food_bungo_pit", "bungo", "Bungo pit"}, {"trash_cherry_pit", "cherry", "Cherry pit"}}) {
            assertModel(pit[0], "moonstation14:item/botany/" + pit[0]);
            same(SOURCE + pit[1] + ".rsi/pit.png", "textures/item/botany/" + pit[0] + ".png");
            assertTrue(language.get("item.moonstation14." + pit[0]).getAsString().equals(pit[2]));
        }
        // Existing log/cloth material sprites are registered already; do not fork or replace them.
        for (String id : new String[]{"cotton_material", "pyrotton_material", "wood", "steel", "paper"}) {
            var existing = json(ROOT + "models/item/" + id + ".json");
            assertEquals("minecraft:item/generated", existing.get("parent").getAsString(), id);
            String texture = existing.getAsJsonObject("textures").get("layer0").getAsString().substring("moonstation14:item/".length());
            try (var stream = resource(ROOT + "textures/item/" + texture + ".png")) { assertNotNull(stream, id); }
        }
    }

    @Test void glassShardUsesCanonicalSourceSpriteAndRetainsProvenance() throws Exception {
        var model = json(ROOT + "models/item/shard_glass.json");
        assertEquals("moonstation14:ss14/Objects/Materials/Shards/shard.rsi/shard1",
                model.getAsJsonObject("textures").get("layer0").getAsString());
        String source = ROOT + "textures/ss14/Objects/Materials/Shards/shard.rsi/";
        var metadata = json(source + "meta.json");
        assertEquals("CC-BY-SA-3.0", metadata.get("license").getAsString());
        assertEquals("Taken from cev-eris at https://github.com/discordia-space/CEV-Eris/commit/b5b4edd4e46efc2ee2d3c1ce5aff82d80ddc461e",
                metadata.get("copyright").getAsString());
        for (String state : new String[]{"shard1", "shard2", "shard3"}) {
            try (var stream = resource(source + state + ".png")) {
                assertNotNull(ImageIO.read(stream), state);
            }
        }
    }

    @Test void carrotSeedAndBananaPeelItemsBindCanonicalSprites() throws Exception {
        assertModel("carrots_seeds", "moonstation14:item/botany/carrot_seed");
        assertModel("trash_banana_peel", "moonstation14:ss14/botany/Objects/Specific/Hydroponics/banana.rsi/peel");
        assertModel("trash_mimana_peel", "moonstation14:ss14/botany/Objects/Specific/Hydroponics/mimana.rsi/peel");
        for (String path : new String[]{
                ROOT + "textures/item/botany/carrot_seed.png",
                ROOT + "textures/ss14/botany/Objects/Specific/Hydroponics/banana.rsi/peel.png",
                ROOT + "textures/ss14/botany/Objects/Specific/Hydroponics/mimana.rsi/peel.png"}) {
            try (var stream = resource(path)) { assertNotNull(ImageIO.read(stream), path); }
        }
    }

    private static void assertModel(String id, String... layers) throws Exception {
        var model = json(ROOT + "models/item/" + id + ".json");
        assertEquals("minecraft:item/generated", model.get("parent").getAsString());
        var textures = model.getAsJsonObject("textures");
        for (int i = 0; i < layers.length; i++) assertEquals(layers[i], textures.get("layer" + i).getAsString());
    }

    private static void assertAnimationSchedule(com.google.gson.JsonObject animation, int sourceFrames,
                                               int atlasWidth, int atlasHeight) {
        assertAnimationDimensionsAndIndices(animation, atlasWidth, atlasHeight);
        int frameWidth = animation.get("width").getAsInt();
        int frameHeight = animation.get("height").getAsInt();
        int atlasFrameCount = (atlasWidth / frameWidth) * (atlasHeight / frameHeight);
        var frames = animation.getAsJsonArray("frames");
        assertEquals(sourceFrames * 5, frames.size());
        int totalTicks = 0;
        for (int i = 0; i < frames.size(); i++) {
            var frame = frames.get(i).getAsJsonObject();
            int frameIndex = frame.get("index").getAsInt();
            assertEquals(i % sourceFrames, frameIndex);
            assertTrue(frameIndex >= 0 && frameIndex < atlasFrameCount);
            int expectedTicks = (int) Math.round((i + 1) * 1.6) - (int) Math.round(i * 1.6);
            assertEquals(expectedTicks, frame.get("time").getAsInt());
            totalTicks += frame.get("time").getAsInt();
        }
        assertEquals(sourceFrames == 23 ? 184 : 176, totalTicks);
    }

    private static void assertAnimationDimensionsAndIndices(com.google.gson.JsonObject animation,
                                                             int atlasWidth, int atlasHeight) {
        int frameWidth = animation.get("width").getAsInt();
        int frameHeight = animation.get("height").getAsInt();
        assertEquals(32, frameWidth);
        assertEquals(32, frameHeight);
        assertEquals(0, atlasWidth % frameWidth);
        assertEquals(0, atlasHeight % frameHeight);
        int atlasFrameCount = (atlasWidth / frameWidth) * (atlasHeight / frameHeight);
        var frames = animation.getAsJsonArray("frames");
        for (var entry : frames) {
            int frameIndex = entry.isJsonObject()
                    ? entry.getAsJsonObject().get("index").getAsInt()
                    : entry.getAsInt();
            assertTrue(frameIndex >= 0 && frameIndex < atlasFrameCount);
        }
    }

    private static void same(String source, String item) throws Exception {
        String itemPath = item.startsWith("textures/") ? item : "textures/item/botany/" + item;
        assertArrayEquals(hash(source.startsWith("/") ? source : SOURCE + source), hash(ROOT + itemPath));
    }

    private static byte[] hash(String path) throws Exception {
        try (var stream = resource(path)) { return MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()); }
    }

    private static com.google.gson.JsonObject json(String path) throws Exception {
        try (var stream = resource(path)) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static java.io.InputStream resource(String path) {
        var stream = BotanyCatalogItemResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return stream;
    }
}
