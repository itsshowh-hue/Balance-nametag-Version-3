package com.example.balancenametagserver;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pushes Vault balances of online players to clients on channel "balancenametag:balances".
 * Format: see README of the client mod. Only changes are sent after the initial snapshot.
 */
public final class BalanceNametagServer extends JavaPlugin implements Listener {
    private static final String CHANNEL = "balancenametag:balances";
    private static final String PERM_RECEIVE = "balancenametag.receive";
    private static final String PERM_HIDE = "balancenametag.hide";
    /** Bukkit plugin messages are limited to 32766 bytes; 1000 * 24 + 6 stays well below. */
    private static final int CHUNK = 1000;

    private final Map<UUID, Double> lastSent = new HashMap<>();
    private boolean warnedNoEconomy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        long interval = Math.max(20L, getConfig().getLong("interval-ticks", 100L));
        getServer().getScheduler().runTaskTimer(this, this::tick, 40L, interval);
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            if (!warnedNoEconomy) {
                getLogger().warning("No Vault economy provider found yet; balances will not be sent.");
                warnedNoEconomy = true;
            }
            return null;
        }
        warnedNoEconomy = false;
        return rsp.getProvider();
    }

    private void tick() {
        Economy eco = economy();
        if (eco == null) {
            return;
        }
        Map<UUID, Double> current = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERM_HIDE)) {
                continue;
            }
            try {
                double v = eco.getBalance(p);
                if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                    current.put(p.getUniqueId(), v);
                }
            } catch (Exception e) {
                getLogger().warning("Could not read balance of " + p.getName() + ": " + e.getMessage());
            }
        }

        Map<UUID, Double> delta = new HashMap<>();
        for (Map.Entry<UUID, Double> e : current.entrySet()) {
            if (!Objects.equals(lastSent.get(e.getKey()), e.getValue())) {
                delta.put(e.getKey(), e.getValue());
            }
        }
        for (UUID gone : lastSent.keySet()) {
            if (!current.containsKey(gone)) {
                delta.put(gone, Double.NaN); // NaN = client forgets this player
            }
        }
        lastSent.clear();
        lastSent.putAll(current);

        if (delta.isEmpty()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.hasPermission(PERM_RECEIVE)) {
                send(viewer, false, delta);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        // Small delay so the client has finished joining and registered its channels.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (viewer.isOnline() && viewer.hasPermission(PERM_RECEIVE)) {
                send(viewer, true, new HashMap<>(lastSent));
            }
        }, 40L);
    }

    private void send(Player to, boolean replaceAll, Map<UUID, Double> balances) {
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(balances.entrySet());
        if (entries.isEmpty() && !replaceAll) {
            return;
        }
        int offset = 0;
        boolean first = true;
        do {
            int end = Math.min(entries.size(), offset + CHUNK);
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bos);
                out.writeByte(1);
                out.writeByte(replaceAll && first ? 1 : 0);
                out.writeInt(end - offset);
                for (int i = offset; i < end; i++) {
                    Map.Entry<UUID, Double> e = entries.get(i);
                    out.writeLong(e.getKey().getMostSignificantBits());
                    out.writeLong(e.getKey().getLeastSignificantBits());
                    out.writeDouble(e.getValue());
                }
                to.sendPluginMessage(this, CHANNEL, bos.toByteArray());
            } catch (IOException | RuntimeException ex) {
                getLogger().warning("Failed to send balances to " + to.getName() + ": " + ex.getMessage());
                return;
            }
            offset = end;
            first = false;
        } while (offset < entries.size());
    }
}
