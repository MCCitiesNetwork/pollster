package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.SoundPlayer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Everything the player screens need. {@code events} fires Bukkit events on the main thread
 * (the composition root passes {@code server.getPluginManager()::callEvent}).
 *
 * <p>Messages and settings are read through suppliers so {@code /pollster reload} takes effect
 * without rebuilding this record or anything that holds it.
 *
 * <p>{@code players} looks a player up by UUID (the composition root passes
 * {@code server::getPlayer}). Sessions outlive a disconnect, and the {@code Player} object from
 * before a rejoin no longer tracks permissions, so permission checks always go through it.
 */
public record PlayerUi(@NotNull SurveyService service, @NotNull SessionRegistry sessions,
                       @NotNull RewardRunner rewards, @NotNull Supplier<Messages> messagesSource,
                       @NotNull Style style, @NotNull Supplier<Settings> settingsSource,
                       @NotNull SoundPlayer sounds, @NotNull Consumer<Event> events,
                       @NotNull Function<UUID, Optional<Player>> players) {

    /** Fixed messages and settings, for tests. */
    public PlayerUi(@NotNull SurveyService service, @NotNull SessionRegistry sessions,
                    @NotNull RewardRunner rewards, @NotNull Messages messages, @NotNull Style style,
                    @NotNull Settings settings, @NotNull SoundPlayer sounds, @NotNull Consumer<Event> events,
                    @NotNull Function<UUID, Optional<Player>> players) {
        this(service, sessions, rewards, () -> messages, style, () -> settings, sounds, events, players);
    }

    /** Whether the player, as currently online, has {@code node}. Offline players have nothing. */
    public boolean hasPermission(@NotNull UUID player, @NotNull String node) {
        return this.players.apply(player).map(online -> online.hasPermission(node)).orElse(false);
    }

    public @NotNull Messages messages() {
        return this.messagesSource.get();
    }

    public @NotNull Settings settings() {
        return this.settingsSource.get();
    }
}
