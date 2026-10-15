package com.example.balancenametag.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** JSON config stored at config/balancenametag.json. */
public final class BalanceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public boolean showForSelf = true;

    public boolean serverPayloadProviderEnabled = true;
    public boolean scoreboardProviderEnabled = true;
    /** Checked in order, case-insensitively. Only these objectives are ever treated as money. */
    public List<String> scoreboardObjectives = new ArrayList<>(List.of("money", "balance", "bal", "coins", "cash"));

    /**
     * Optional HTTPS JSON API (only useful if the server operator offers one).
     * URL may contain {name} and {uuid}. Disabled by default.
     */
    public boolean httpApiEnabled = false;
    public String httpApiUrl = "";
    public String httpApiKeyHeader = "Authorization";
    public String httpApiKey = "";
    /** Dot-separated path to the balance inside the JSON response, e.g. "data.balance". */
    public String httpApiBalanceField = "balance";
    public int httpApiRefreshSeconds = 60;
    public int httpApiMinIntervalMs = 500;

    public String currencySymbol = "$";
    /** Shown when no balance is known. Empty string = hide the second line. */
    public String placeholder = "";
    public String textColor = "#55FF55";

    /** How often (in ticks) a player's cached balance is re-read. */
    public int refreshTicks = 20;
    /** Balance line is not drawn beyond this many blocks. */
    public double maxDistance = 32.0;
    /** Slightly enlarges the balance line with distance so it stays legible. */
    public boolean distanceScaling = true;
    public float maxScale = 2.0f;

    private transient int rgb = 0x55FF55;

    public int rgb() {
        return rgb;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("balancenametag.json");
    }

    public static BalanceConfig load(Logger log) {
        Path p = path();
        BalanceConfig cfg = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, BalanceConfig.class);
            } catch (Exception e) {
                log.error("Could not read {} - using defaults (file left untouched)", p, e);
                BalanceConfig fallback = new BalanceConfig();
                fallback.sanitize(log);
                return fallback;
            }
        }
        boolean fresh = cfg == null;
        if (fresh) {
            cfg = new BalanceConfig();
        }
        cfg.sanitize(log);
        if (fresh) {
            cfg.save(log);
        }
        return cfg;
    }

    public void save(Logger log) {
        Path p = path();
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            log.error("Could not write {}", p, e);
        }
    }

    private void sanitize(Logger log) {
        if (scoreboardObjectives == null) {
            scoreboardObjectives = new ArrayList<>();
        }
        List<String> cleaned = new ArrayList<>();
        for (String s : scoreboardObjectives) {
            if (s != null && !s.isBlank()) {
                cleaned.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        scoreboardObjectives = cleaned;
        if (currencySymbol == null) currencySymbol = "";
        if (placeholder == null) placeholder = "";
        if (httpApiUrl == null) httpApiUrl = "";
        if (httpApiKeyHeader == null || httpApiKeyHeader.isBlank()) httpApiKeyHeader = "Authorization";
        if (httpApiKey == null) httpApiKey = "";
        if (httpApiBalanceField == null || httpApiBalanceField.isBlank()) httpApiBalanceField = "balance";
        httpApiRefreshSeconds = Math.max(10, Math.min(3600, httpApiRefreshSeconds));
        httpApiMinIntervalMs = Math.max(100, Math.min(10000, httpApiMinIntervalMs));
        refreshTicks = Math.max(1, Math.min(1200, refreshTicks));
        maxDistance = Math.max(4.0, Math.min(64.0, maxDistance));
        maxScale = Math.max(1.0f, Math.min(4.0f, maxScale));
        try {
            String c = textColor == null ? "" : textColor.trim();
            if (c.startsWith("#")) c = c.substring(1);
            rgb = Integer.parseInt(c, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            log.warn("Invalid textColor '{}', using #55FF55", textColor);
            textColor = "#55FF55";
            rgb = 0x55FF55;
        }
    }
}
