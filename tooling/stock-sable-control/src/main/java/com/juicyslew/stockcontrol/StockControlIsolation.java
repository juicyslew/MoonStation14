package com.juicyslew.stockcontrol;

import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixins;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime proof that this diagnostic uses stock Sable in an isolated loader. */
public final class StockControlIsolation {
    private static final String EXPECTED_SABLE_SHA256 = "9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2";
    private static final String EXPECTED_MINECRAFT = "1.21.1";
    private static final String EXPECTED_NEOFORGE = "21.1.228";
    private static final String EXPECTED_SABLE = "2.0.6";
    private static final AtomicBoolean VERIFIED = new AtomicBoolean();

    private StockControlIsolation() { }

    public static void verify() {
        if (VERIFIED.get()) return;
        synchronized (VERIFIED) {
            if (VERIFIED.get()) return;
            var logger = org.slf4j.LoggerFactory.getLogger("StockSableControl");
            var mods = ModList.get().getMods();
            logger.info("[stock-control-isolation] loaded-mods={}", mods.stream()
                    .map(m -> m.getModId() + "=" + m.getVersion()).sorted().toList());
            if (mods.stream().anyMatch(m -> m.getModId().equals("moonstation14")))
                throw new IllegalStateException("MoonStation14 is present in stock-only runtime");
            Set<String> allowed = Set.of("minecraft", "neoforge", "sable", "sablecompanion", "veil", "sable_control");
            var unexpected = mods.stream().map(m -> m.getModId()).filter(id -> !allowed.contains(id)).toList();
            if (!unexpected.isEmpty()) throw new IllegalStateException("Unexpected mods in stock-only runtime: " + unexpected);
            try {
                if (StockControlIsolation.class.getClassLoader().getResource("com/juicyslew/moonstation14/MoonStation14.class") != null
                        || StockControlIsolation.class.getClassLoader().getResource("moonstation14.gametest.mixins.json") != null)
                    throw new IllegalStateException("MoonStation integration class or mixin config is on runtime classpath");
                var configs = Mixins.getConfigs().stream().map(c -> c.getName()).sorted().toList();
                if (configs.stream().anyMatch(n -> n.toLowerCase().contains("moonstation14")))
                    throw new IllegalStateException("MoonStation mixin config loaded: " + configs);
                logger.info("[stock-control-isolation] loaded-mixin-configs={}", configs);
                String gameVersion = net.minecraft.SharedConstants.getCurrentVersion().getName();
                String neoForgeVersion = ModList.get().getModContainerById("neoforge").orElseThrow()
                        .getModInfo().getVersion().toString();
                String sableVersion = ModList.get().getModContainerById("sable").orElseThrow()
                        .getModInfo().getVersion().toString();
                logger.info("[stock-control-isolation] game={} neoforge={} sable={}",
                        gameVersion, neoForgeVersion, sableVersion);
                if (!EXPECTED_MINECRAFT.equals(gameVersion) || !EXPECTED_NEOFORGE.equals(neoForgeVersion)
                        || !EXPECTED_SABLE.equals(sableVersion))
                    throw new IllegalStateException("Unexpected runtime versions: Minecraft=" + gameVersion
                            + ", NeoForge=" + neoForgeVersion + ", Sable=" + sableVersion);
                Path sableJar = ModList.get().getModContainerById("sable").orElseThrow()
                        .getModInfo().getOwningFile().getFile().getFilePath();
                if (!Files.isRegularFile(sableJar)) throw new IllegalStateException("Sable owning file is not a regular jar: " + sableJar);
                String digest;
                try (InputStream input = Files.newInputStream(sableJar)) {
                    var md = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = input.read(buffer)) >= 0) md.update(buffer, 0, count);
                    digest = HexFormat.of().formatHex(md.digest());
                }
                logger.info("[stock-control-isolation] sable-jar={} sha256={}", sableJar, digest);
                if (!EXPECTED_SABLE_SHA256.equals(digest)) throw new IllegalStateException("Unexpected Sable artifact digest " + digest);
                logger.info("[stock-control-isolation] verified-source-binding={}",
                        StockControlIsolation.class.getProtectionDomain().getCodeSource().getLocation());
                VERIFIED.set(true);
            } catch (Exception failure) {
                throw new IllegalStateException("Stock-only runtime isolation proof failed", failure);
            }
        }
    }
}
