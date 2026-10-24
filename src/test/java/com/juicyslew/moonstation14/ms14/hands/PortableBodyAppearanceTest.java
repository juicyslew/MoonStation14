package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures;
import com.juicyslew.moonstation14.ms14.mob.mouse.PortableMouseIconTextures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class PortableBodyAppearanceTest {
    private static final java.util.List<PortableBodyAppearance.Coat> MOUSE_COATS = java.util.List.of(
            PortableBodyAppearance.Coat.WHITE, PortableBodyAppearance.Coat.BROWN, PortableBodyAppearance.Coat.GRAY);

    @Test void itemPresentationCacheSelectsIconAndHeldWorldTextureForEveryVisualView() {
        for (var coat : MOUSE_COATS) for (var state : PortableBodyAppearance.State.values()) {
            var view = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse"), "Mittens", coat, state);
            var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
            source.set(com.juicyslew.moonstation14.component.ModDataComponents.PORTABLE_BODY_APPEARANCE.get(), view);
            var snapshot = com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation.fromStack(source);
            var cached = com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.fromStack(snapshot.stack());
            assertEquals(view, cached);
            assertEquals(PortableMouseIconTextures.frame(view), PortableMouseIconTextures.frame(cached));
            var held = MouseAppearanceTextures.heldPresentation(cached);
            boolean dead = state == PortableBodyAppearance.State.DEAD;
            String color = switch (coat) { case WHITE -> "white"; case BROWN -> "brown"; case GRAY -> "gray";
                case NONE -> throw new AssertionError("NONE is not a mouse coat"); };
            assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/mouse/mouse_"
                    + color + (dead ? "_dead" : "") + ".png"), held.texture());
            assertEquals(state == PortableBodyAppearance.State.ALIVE ? "rest" : "down_hold", held.pose());
            assertEquals(dead, held.dead());
        }
    }
    @Test void noneCoatIsRejectedByMouseRendererAndUsedByAnimalDescriptor() {
        var none = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "moproach"),
                "moproach", PortableBodyAppearance.Coat.NONE, PortableBodyAppearance.State.ALIVE, "moproach");
        assertThrows(IllegalArgumentException.class, () -> MouseAppearanceTextures.heldPresentation(none));
        assertNotNull(PortableBodyIconTextures.frame(none));
    }
    @Test void appearanceCodecIsBoundedAndContainsOnlyVisualIdentity() {
        var view = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse"),
                "Mittens", PortableBodyAppearance.Coat.BROWN, PortableBodyAppearance.State.CRITICAL);
        var encoded = PortableBodyAppearance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, view).result().orElseThrow();
        assertEquals(view, PortableBodyAppearance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).result().orElseThrow());
         assertEquals(java.util.Set.of("profile", "name", "coat", "state", "visualKey"),
                java.util.Arrays.stream(PortableBodyAppearance.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> new PortableBodyAppearance(view.profile(), "x".repeat(129), view.coat(), view.state()));
    }

    @Test void registeredAnimalKeysSelectRealSourceFramesWithoutMouseFallback() throws Exception {
        for (String key : java.util.List.of("hamster", "bee", "butterfly", "cockroach", "mothroach", "moproach")) {
            assertTrue(PortableBodyIconTextures.isRegistered(key));
            for (var state : PortableBodyAppearance.State.values()) {
                var view = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", key), key,
                        PortableBodyAppearance.Coat.NONE, state, key);
                var frame = PortableBodyIconTextures.frame(view);
                assertNotNull(frame);
                var png = projectPath("src/main/resources/assets/moonstation14/" + frame.texture().getPath());
                var image = ImageIO.read(png.toFile());
                assertNotNull(image);
                assertTrue(frame.sourceX() + frame.sourceWidth() <= image.getWidth());
                assertTrue(frame.sourceY() + frame.sourceHeight() <= image.getHeight());
                assertTrue(Files.isRegularFile(png.getParent().resolve("meta.json")));
                BufferedImage selectedFrame = image.getSubimage(frame.sourceX(), frame.sourceY(), frame.sourceWidth(), frame.sourceHeight());
                boolean hasVisiblePixel = false;
                for (int y = 0; y < selectedFrame.getHeight() && !hasVisiblePixel; y++)
                    for (int x = 0; x < selectedFrame.getWidth(); x++)
                        if ((selectedFrame.getRGB(x, y) >>> 24) != 0) { hasVisiblePixel = true; break; }
                assertTrue(hasVisiblePixel, key + "/" + state + " selected frame has visible pixels: " + png);
            }
        }
        assertFalse(PortableBodyIconTextures.isRegistered("unknown_animal"));
        var unknown = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "unknown"), "unknown",
                PortableBodyAppearance.Coat.NONE, PortableBodyAppearance.State.ALIVE, "unknown_animal");
        assertNull(PortableBodyIconTextures.frame(unknown));
    }

    @Test void productionDescriptorTargetsImportedSingleFrameIcons() throws Exception {
        for (var coat : MOUSE_COATS) {
            assertEquals(coat.ordinal(), PortableMouseIconTextures.variantColumn(coat));
            for (var state : PortableBodyAppearance.State.values()) {
                var view = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse"), "Mittens", coat, state);
                var frame = PortableMouseIconTextures.frame(view);
                var icon = projectPath("src/main/resources/assets/moonstation14/" + frame.texture().getPath());
                assertTrue(Files.isRegularFile(icon), "pinned RSI frame imported: " + icon);
                assertEquals(new PortableMouseIconTextures.Frame(frame.texture(), 0, 0, 32, 32, 32, 32, 16, 16), frame);
            }
        }
    }

    @Test void actualIconFramesUseProductionDescriptorAndProduceLabeledContactSheet() throws Exception {
        BufferedImage sheet = new BufferedImage(360, 300, BufferedImage.TYPE_INT_ARGB);
        var graphics = sheet.createGraphics();
        graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.setColor(java.awt.Color.BLACK);
        graphics.drawString("Rows: alive / critical / dead; columns: white / brown / gray. 16px icons: hand (left), grid (right).", 4, 12);
        graphics.drawString("CPU source-frame crop; GPU mesh clipping is not represented.", 4, 26);
        var colors = new java.util.HashSet<Integer>();
        var fingerprints = new java.util.HashSet<Integer>();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for (var state : PortableBodyAppearance.State.values()) {
            graphics.drawString(state.name().toLowerCase(), 4, 52 + state.ordinal() * 78);
            for (var coat : MOUSE_COATS) {
                var view = new PortableBodyAppearance(ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse"), "Mittens", coat, state);
                var frame = PortableMouseIconTextures.frame(view);
                var file = projectPath("src/main/resources/assets/moonstation14/" + frame.texture().getPath());
            BufferedImage source = ImageIO.read(file.toFile());
            assertNotNull(source, "PNG decodes: " + file);
            assertEquals(expectedPngHash(state, coat), java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))),
                    "must remain the byte-exact pinned RSI frame");
                assertEquals(32, source.getWidth());
                assertEquals(32, source.getHeight());
                assertEquals(frame.sheetWidth(), source.getWidth());
                assertEquals(frame.sheetHeight(), source.getHeight());
                graphics.drawString(coat.name().toLowerCase(), 50 + coat.ordinal() * 100, 42);
                BufferedImage crop = source.getSubimage(frame.sourceX(), frame.sourceY(), frame.sourceWidth(), frame.sourceHeight());
                int visible = 0;
                int fingerprint = 1;
                var rgba = java.security.MessageDigest.getInstance("SHA-256");
                for (int y = 0; y < crop.getHeight(); y++) for (int x = 0; x < crop.getWidth(); x++) {
                    int argb = crop.getRGB(x, y);
                    fingerprint = 31 * fingerprint + argb;
                    rgba.update((byte) (argb >>> 24)); rgba.update((byte) (argb >>> 16));
                    rgba.update((byte) (argb >>> 8)); rgba.update((byte) argb);
                    if ((argb >>> 24) != 0) { visible++; colors.add(argb); }
                }
                assertEquals(expectedRgbaHash(state, coat), java.util.HexFormat.of().formatHex(rgba.digest()),
                        "decoded RGBA matches pinned RSI source frame");
                assertTrue(visible > 0, state + "/" + coat + " has visible source pixels");
                assertTrue(fingerprints.add(fingerprint), state + "/" + coat + " source frame is distinct");
                assertEquals(16, frame.drawWidth()); assertEquals(16, frame.drawHeight());
                int px = 50 + coat.ordinal() * 100;
                int py = 48 + state.ordinal() * 78;
                graphics.drawImage(crop, px, py, frame.drawWidth(), frame.drawHeight(), null);
                graphics.drawRect(px - 1, py - 1, 17, 17); // hand bounds
                graphics.drawImage(crop, px + 24, py, frame.drawWidth(), frame.drawHeight(), null);
                graphics.drawRect(px + 23, py - 1, 17, 17); // grid bounds
            }
        }
        graphics.dispose();
        assertTrue(colors.size() > 8, "selected frames contain multiple actual colors");
        Path output = Path.of("build/reports/living-carry-preview/mouse-appearance-contact-sheet.png");
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(sheet, "png", output.toFile()));
    }

    private static String expectedPngHash(PortableBodyAppearance.State state, PortableBodyAppearance.Coat coat) {
        String[][] hashes = {
                {"2b12d45fdb79904641b9b652b0d89e6bf5c2659934e7b7cd891f26bc7ca38a33", "0906509f69947c584dbc2186109f70a8e233831e2f0be245753e02639ecd86e3", "24da3fbac4e9f89ee7ef593d8c271303ead82e74a85da538755fddc80dc27348"},
                {"3e26646f1f2405d55ba9567ac4442204ecd57028e739358719fcc93ab9b6f5df", "e6df89731135e60d6f425920bb7b41777de9cc1969a2a153962d120152b088f8", "5911efc1541e0e463fc220c8d4faa07a22196458a21cbf5819a98253a7257eb5"},
                {"9da3bec7303759a633bd000061b70abd8feaa8c5234296f0bc499193b2cda7d3", "5669b005bc5240c110a874582e7cf6ba591eedfcb561bb88cd9d95bcce839f1f", "45504f14fc58f373b980fff5d99a4be9cf229c5212c8685d0161c7e6a0d28831"}
        };
        int stateIndex = switch (state) { case ALIVE -> 0; case CRITICAL -> 1; case DEAD -> 2; };
        return hashes[stateIndex][coat.ordinal()];
    }

    private static String expectedRgbaHash(PortableBodyAppearance.State state, PortableBodyAppearance.Coat coat) {
        String[][] hashes = {
                {"9a57a15be2bdbace77c8185f6a6969cf4da11c218b15c4956115dc94ce9937ba", "eaacb5dca3e33d04ef542c39fdafc31b5c466020670e074940e9283d385923fc", "cbb213201e15deaa2b8869a8bf50063691bea5ad0cfa8f8c7df0d37df9661620"},
                {"cccc8240ec8f076693a682c218ce6f7de0acad9451f8aba08948f1dfbbde6cd9", "d4a0d4c59f57e32b199a10c7ffc7002ea99ea26bd35e05eec06b3f54dac4fac8", "6dbde1fe5ade2242c5047eed70e0a2321aba6a29a6aa5815e890d137edd65e2f"},
                {"185a84cefaaabb8f6cf6bfe70ae4e05f18765afbdba2b85a214a8e7c5b60a797", "6ca3fb5d9d1151e08a23c7b356190d1881541d2f9fd6ae7442a562a6f607e2cd", "89607665468197cbf714bf78b7adbcb09b8be6f5c9da58d1e21f93ba0122e355"}
        };
        int stateIndex = switch (state) { case ALIVE -> 0; case CRITICAL -> 1; case DEAD -> 2; };
        return hashes[stateIndex][coat.ordinal()];
    }

    private static Path projectPath(String path) throws java.io.IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new java.io.IOException("Could not locate project file " + path);
    }
}
