package com.example.balancenametag.provider;

import com.example.balancenametag.config.BalanceConfig;
import com.example.balancenametag.BalanceNametagClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.OptionalDouble;

/**
 * Reads a score from a scoreboard objective whose name is in the configured list.
 * Only data the server synced to this client is visible; nothing else is touched.
 */
public final class ScoreboardBalanceProvider implements BalanceProvider {
    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public boolean isEnabled(BalanceConfig config) {
        return config.scoreboardProviderEnabled;
    }

    @Override
    public OptionalDouble getBalance(AbstractClientPlayerEntity player) {
        Scoreboard scoreboard = player.getWorld().getScoreboard();
        if (scoreboard.getObjectives().isEmpty()) {
            return OptionalDouble.empty();
        }
        for (String wanted : BalanceNametagClient.config().scoreboardObjectives) {
            for (ScoreboardObjective objective : scoreboard.getObjectives()) {
                if (!objective.getName().equalsIgnoreCase(wanted)) {
                    continue;
                }
                // Player entities are ScoreHolders; the score is keyed by username.
                ReadableScoreboardScore score = scoreboard.getScore(player, objective);
                if (score != null) {
                    return OptionalDouble.of(score.getScore());
                }
            }
        }
        return OptionalDouble.empty();
    }
}
