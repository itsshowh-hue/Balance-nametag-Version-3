package com.example.balancenametag.provider;

import com.example.balancenametag.config.BalanceConfig;
import com.example.balancenametag.net.BalanceSyncPayload;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Balances pushed by a cooperating server plugin over the
 * "balancenametag:balances" custom payload channel (see README for the format).
 * Nothing is requested from the server; we only listen.
 */
public final class ServerPayloadBalanceProvider implements BalanceProvider {
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "server-payload";
    }

    @Override
    public boolean isEnabled(BalanceConfig config) {
        return config.serverPayloadProviderEnabled;
    }

    public void apply(BalanceSyncPayload payload) {
        if (payload.replaceAll()) {
            balances.clear();
        }
        for (Map.Entry<UUID, Double> e : payload.balances().entrySet()) {
            double v = e.getValue();
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                balances.remove(e.getKey());
            } else {
                balances.put(e.getKey(), v);
            }
        }
    }

    @Override
    public OptionalDouble getBalance(AbstractClientPlayerEntity player) {
        Double v = balances.get(player.getUuid());
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }

    @Override
    public void reset() {
        balances.clear();
    }
}
