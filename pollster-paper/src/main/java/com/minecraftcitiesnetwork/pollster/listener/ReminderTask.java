package com.minecraftcitiesnetwork.pollster.listener;

import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import com.minecraftcitiesnetwork.pollster.config.Messages;
import com.minecraftcitiesnetwork.pollster.config.Settings;
import com.minecraftcitiesnetwork.pollster.session.PlayerSession;
import com.minecraftcitiesnetwork.pollster.session.SessionRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Nudges players who left a survey unfinished. Paper fires no event when a dialog is closed with
 * Escape, so "paused" means idle: no button click for {@code reminders.idle}. Run it every
 * second on the main thread.
 */
public final class ReminderTask implements Runnable {

    private final SessionRegistry sessions;
    private final Clock clock;
    private final Supplier<Settings> settings;
    private final Function<UUID, Optional<Player>> online;
    private final BiConsumer<Player, Component> send;
    private final Supplier<Messages> messages;

    public ReminderTask(@NotNull SessionRegistry sessions, @NotNull Clock clock,
                        @NotNull Supplier<Settings> settings,
                        @NotNull Function<UUID, Optional<Player>> online,
                        @NotNull BiConsumer<Player, Component> send, @NotNull Supplier<Messages> messages) {
        this.sessions = sessions;
        this.clock = clock;
        this.settings = settings;
        this.online = online;
        this.send = send;
        this.messages = messages;
    }

    @Override
    public void run() {
        Settings.Reminders reminders = this.settings.get().reminders();
        if (!reminders.enabled()) {
            return;
        }
        Instant now = this.clock.instant();
        for (PlayerSession session : this.sessions.all()) {
            // Admin previews are not something to nag about.
            if (session.preview() || now.isBefore(session.lastActivity().plus(reminders.idle()))) {
                continue;
            }
            Instant last = session.lastReminder().get();
            if (!due(last, reminders.repeat(), now)) {
                continue;
            }
            Optional<Player> player = this.online.apply(session.player());
            if (player.isEmpty()) {
                continue;
            }
            this.send.accept(player.get(), this.messages.get().messageFor(MessageKeys.REMINDER_MESSAGE));
            session.lastReminder().set(now);
        }
    }

    private static boolean due(Instant last, Duration repeat, Instant now) {
        if (last == null) {
            return true;
        }
        // A zero repeat means one reminder per idle stretch.
        return !repeat.isZero() && !now.isBefore(last.plus(repeat));
    }
}
