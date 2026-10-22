package com.minecraftcitiesnetwork.pollster.listener;

import com.minecraftcitiesnetwork.pollster.FeaturedPicker;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import com.minecraftcitiesnetwork.pollster.ui.player.PlayerFlow;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Offers a featured survey to a player who just joined. */
public final class JoinListener implements Listener {

    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;
    private final Server server;
    private final Logger log;
    private final SurveyService service;
    private final SessionRegistry sessions;
    private final PlayerFlow flow;
    private final Supplier<Settings> settings;
    private final Clock clock;

    public JoinListener(@NotNull Plugin plugin, @NotNull Server server, @NotNull Logger log,
                        @NotNull SurveyService service, @NotNull SessionRegistry sessions,
                        @NotNull PlayerFlow flow, @NotNull Supplier<Settings> settings, @NotNull Clock clock) {
        this.plugin = plugin;
        this.server = server;
        this.log = log;
        this.service = service;
        this.sessions = sessions;
        this.flow = flow;
        this.settings = settings;
        this.clock = clock;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Settings.Featured featured = this.settings.get().featured();
        if (!featured.onJoin()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        List<SurveyDefinition> candidates = this.service.visibleTo(player).stream()
                .filter(SurveyDefinition::featured).toList();
        if (candidates.isEmpty()) {
            return;
        }
        this.service.completed(id)
                .thenCombine(this.service.lastFeatured(id), (completed, last) -> pick(candidates, completed, last))
                .whenComplete((pick, failure) -> {
                    if (failure != null) {
                        this.log.log(Level.WARNING, "Could not check featured surveys for " + player.getName(),
                                failure);
                        return;
                    }
                    pick.ifPresent(survey -> offerLater(player, survey, featured.delay().toMillis()));
                });
    }

    private Optional<SurveyDefinition> pick(List<SurveyDefinition> candidates, Set<String> completed,
                                            Optional<Instant> lastFeatured) {
        Map<String, Instant> openedAt = new HashMap<>();
        for (SurveyDefinition survey : candidates) {
            this.service.cachedState(survey.id()).map(SurveyStateRecord::openedAt)
                    .ifPresent(opened -> openedAt.put(survey.id(), opened));
        }
        Settings.Featured featured = this.settings.get().featured();
        return FeaturedPicker.pick(candidates, completed, openedAt, lastFeatured.orElse(null),
                featured.cooldown(), this.clock.instant());
    }

    private void offerLater(Player player, SurveyDefinition survey, long delayMillis) {
        this.server.getScheduler().runTaskLater(this.plugin, () -> {
            // The player may have left, or started something, during the delay.
            if (!player.isOnline() || this.sessions.get(player.getUniqueId()).isPresent()) {
                return;
            }
            this.flow.openFeatured(player, survey);
            this.service.markFeatured(player.getUniqueId()).exceptionally(failure -> {
                this.log.log(Level.WARNING, "Could not record the featured prompt for " + player.getName(), failure);
                return null;
            });
        }, Math.max(1L, delayMillis / MILLIS_PER_TICK));
    }
}
