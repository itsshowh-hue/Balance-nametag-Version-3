package com.example.balancenametag.provider;

import com.example.balancenametag.BalanceNametagClient;
import com.example.balancenametag.config.BalanceConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/** Queries providers in priority order and caches the formatted result per player. */
public final class BalanceManager {
    private static final int MAX_CACHE = 1024;

    private record Entry(String text, long fetchedAtMs) {
    }

    private final List<BalanceProvider> providers;
    private final Map<UUID, Entry> cache = new HashMap<>();
    private final Set<String> reportedErrors = new HashSet<>();

    public BalanceManager(List<BalanceProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public void clear() {
        cache.clear();
        reportedErrors.clear();
        for (BalanceProvider p : providers) {
            try {
                p.reset();
            } catch (Exception e) {
                BalanceNametagClient.LOGGER.warn("Provider {} failed to reset", p.id(), e);
            }
        }
    }

    /** Returns the text for the second line, or null if nothing should be drawn. Render-thread only. */
    public String getDisplayText(AbstractClientPlayerEntity player) {
        BalanceConfig cfg = BalanceNametagClient.config();
        if (!cfg.enabled) {
            return null;
        }
        if (!cfg.showForSelf && player == MinecraftClient.getInstance().player) {
            return null;
        }
        long now = System.currentTimeMillis();
        UUID id = player.getUuid();
        Entry e = cache.get(id);
        if (e == null || now - e.fetchedAtMs() >= cfg.refreshTicks * 50L) {
            if (cache.size() >= MAX_CACHE) {
                cache.clear();
            }
            e = new Entry(resolve(player, cfg), now);
            cache.put(id, e);
        }
        return e.text();
    }

    private String resolve(AbstractClientPlayerEntity player, BalanceConfig cfg) {
        for (BalanceProvider provider : providers) {
            if (!provider.isEnabled(cfg)) {
                continue;
            }
            try {
                OptionalDouble v = provider.getBalance(player);
                if (v.isPresent() && !Double.isNaN(v.getAsDouble()) && !Double.isInfinite(v.getAsDouble())) {
                    return format(v.getAsDouble(), cfg);
                }
            } catch (Exception ex) {
                if (reportedErrors.add(provider.id())) {
                    BalanceNametagClient.LOGGER.error("Balance provider '{}' threw; further errors suppressed until reload", provider.id(), ex);
                }
            }
        }
        return cfg.placeholder.isEmpty() ? null : cfg.placeholder;
    }

    static String format(double value, BalanceConfig cfg) {
        String sign = value < 0 ? "-" : "";
        double abs = Math.abs(value);
        String number = abs == Math.floor(abs) && abs < 9.0E18
                ? String.format(Locale.US, "%,d", (long) abs)
                : String.format(Locale.US, "%,.2f", abs);
        return sign + cfg.currencySymbol + number;
    }
}
