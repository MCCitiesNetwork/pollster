package com.minecraftcitiesnetwork.pollster.ui.player;

import com.minecraftcitiesnetwork.pollster.SoundPlayer;
import com.minecraftcitiesnetwork.pollster.SurveyService;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.rewards.RewardRunner;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import com.minecraftcitiesnetwork.pollster.ui.Style;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything the player screens need. {@code events} fires Bukkit events on the main thread
 * (the composition root passes {@code server.getPluginManager()::callEvent}).
 *
 * <p>Messages and settings are read through suppliers so {@code /pollster reload} takes effect
 * without rebuilding this record or anything that holds it.
 */
public record PlayerUi(@NotNull SurveyService service, @NotNull SessionRegistry sessions,
                       @NotNull RewardRunner rewards, @NotNull Supplier<Messages> messagesSource,
                       @NotNull Style style, @NotNull Supplier<Settings> settingsSource,
                       @NotNull SoundPlayer sounds, @NotNull Consumer<Event> events) {

    /** Fixed messages and settings, for tests. */
    public PlayerUi(@NotNull SurveyService service, @NotNull SessionRegistry sessions,
                    @NotNull RewardRunner rewards, @NotNull Messages messages, @NotNull Style style,
                    @NotNull Settings settings, @NotNull SoundPlayer sounds, @NotNull Consumer<Event> events) {
        this(service, sessions, rewards, () -> messages, style, () -> settings, sounds, events);
    }

    public @NotNull Messages messages() {
        return this.messagesSource.get();
    }

    public @NotNull Settings settings() {
        return this.settingsSource.get();
    }
}
