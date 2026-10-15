package com.example.balancenametag.provider;

import com.example.balancenametag.config.BalanceConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.OptionalDouble;

/**
 * A source of balance data. Implementations must only use data the server has
 * deliberately made available to the client, and must return empty when unknown.
 */
public interface BalanceProvider {
    String id();

    boolean isEnabled(BalanceConfig config);

    OptionalDouble getBalance(AbstractClientPlayerEntity player);

    /** Called on disconnect / config change. */
    default void reset() {
    }
}
