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

/**
 * Everything the player screens need. {@code events} fires Bukkit events on the main thread
 * (the composition root passes {@code server.getPluginManager()::callEvent}).
 */
public record PlayerUi(@NotNull SurveyService service, @NotNull SessionRegistry sessions,
                       @NotNull RewardRunner rewards, @NotNull Messages messages, @NotNull Style style,
                       @NotNull Settings settings, @NotNull SoundPlayer sounds,
                       @NotNull Consumer<Event> events) {
}
