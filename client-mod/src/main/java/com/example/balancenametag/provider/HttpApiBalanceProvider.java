package com.example.balancenametag.provider;

import com.example.balancenametag.BalanceNametagClient;
import com.example.balancenametag.config.BalanceConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optional adapter for a server operator's own HTTPS JSON API. Disabled by default and useless
 * unless the server really offers such an API (and, if needed, gives you a key).
 * Never blocks the render thread: values are fetched asynchronously, cached, and rate limited.
 */
public final class HttpApiBalanceProvider implements BalanceProvider {
    private static final int MAX_BODY = 65536;

    private record Cached(OptionalDouble value, long fetchedAtMs) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextAllowedMs = new AtomicLong();
    private volatile HttpClient client;

    @Override
    public String id() {
        return "http-api";
    }

    @Override
    public boolean isEnabled(BalanceConfig config) {
        return config.httpApiEnabled && !config.httpApiUrl.isBlank();
    }

    @Override
    public void reset() {
        cache.clear();
        reported.clear();
        nextAllowedMs.set(0L);
    }

    @Override
    public OptionalDouble getBalance(AbstractClientPlayerEntity player) {
        BalanceConfig cfg = BalanceNametagClient.config();
        String name = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        Cached c = cache.get(name);
        if (c == null || now - c.fetchedAtMs() >= cfg.httpApiRefreshSeconds * 1000L) {
            fetch(cfg, name, player.getUuid().toString(), now);
        }
        return c == null ? OptionalDouble.empty() : c.value();
    }

    private HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            c = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            client = c;
        }
        return c;
    }

    private void fetch(BalanceConfig cfg, String name, String uuid, long now) {
        if (!cfg.httpApiUrl.startsWith("https://")) {
            warnOnce("url", "httpApiUrl must start with https:// - request skipped");
            return;
        }
        if (!inFlight.add(name)) {
            return;
        }
        long allowed = nextAllowedMs.get();
        if (now < allowed || !nextAllowedMs.compareAndSet(allowed, now + cfg.httpApiMinIntervalMs)) {
            inFlight.remove(name);
            return;
        }
        HttpRequest request;
        try {
            String url = cfg.httpApiUrl
                    .replace("{name}", URLEncoder.encode(name, StandardCharsets.UTF_8))
                    .replace("{uuid}", uuid);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET();
            if (!cfg.httpApiKey.isBlank()) {
                b.header(cfg.httpApiKeyHeader, cfg.httpApiKey);
            }
            request = b.build();
        } catch (RuntimeException e) {
            inFlight.remove(name);
            warnOnce("badurl", "httpApiUrl is not a valid URL: " + e.getMessage());
            return;
        }
        final String field = cfg.httpApiBalanceField;
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
            try {
                OptionalDouble value = OptionalDouble.empty();
                if (err != null) {
                    warnOnce("net", "Balance API request failed: " + err.getClass().getSimpleName());
                } else if (resp.statusCode() == 429) {
                    nextAllowedMs.set(System.currentTimeMillis() + 30_000L);
                    warnOnce("429", "Balance API rate-limited us (HTTP 429); backing off for 30s");
                } else if (resp.statusCode() == 200 && resp.body().length() <= MAX_BODY) {
                    value = extract(resp.body(), field);
                } else if (resp.statusCode() != 200 && resp.statusCode() != 404) {
                    warnOnce("http" + resp.statusCode(), "Balance API returned HTTP " + resp.statusCode());
                }
                if (cache.size() > 2048) {
                    cache.clear();
                }
                cache.put(name, new Cached(value, System.currentTimeMillis()));
            } catch (RuntimeException e) {
                warnOnce("parse", "Could not process balance API response: " + e.getMessage());
            } finally {
                inFlight.remove(name);
            }
        });
    }

    private static OptionalDouble extract(String body, String path) {
        try {
            JsonElement el = JsonParser.parseString(body);
            for (String part : path.split("\\.")) {
                if (!el.isJsonObject()) {
                    return OptionalDouble.empty();
                }
                JsonObject o = el.getAsJsonObject();
                if (!o.has(part)) {
                    return OptionalDouble.empty();
                }
                el = o.get(part);
            }
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isNumber()) {
                    return OptionalDouble.of(el.getAsDouble());
                }
                if (el.getAsJsonPrimitive().isString()) {
                    return OptionalDouble.of(Double.parseDouble(el.getAsString().trim()));
                }
            }
        } catch (RuntimeException ignored) {
            // malformed JSON or non-numeric value: treated as "unknown"
        }
        return OptionalDouble.empty();
    }

    private void warnOnce(String key, String message) {
        if (reported.add(key)) {
            BalanceNametagClient.LOGGER.warn(message);
        }
    }
}
