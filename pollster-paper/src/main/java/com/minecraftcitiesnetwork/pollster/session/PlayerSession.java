package com.minecraftcitiesnetwork.pollster.session;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One player's unfinished survey, held in memory only.
 *
 * <p>{@code token} is unique per started session, so a dialog button captured before the session
 * was replaced can tell it is stale. {@code submitting} is shared by every copy of the session
 * (touching makes a new record) and blocks a second Submit while the first is still saving.
 * {@code lastReminder} belongs to one idle stretch: any activity starts a fresh one.
 */
public record PlayerSession(@NotNull UUID player, @NotNull SurveyFlow flow, boolean preview, long token,
                            @NotNull Instant lastActivity, @NotNull AtomicBoolean submitting,
                            @NotNull AtomicReference<@Nullable Instant> lastReminder) {

    public PlayerSession(@NotNull UUID player, @NotNull SurveyFlow flow, boolean preview, long token,
                         @NotNull Instant lastActivity) {
        this(player, flow, preview, token, lastActivity, new AtomicBoolean(), new AtomicReference<>());
    }

    @NotNull PlayerSession touched(@NotNull Instant now) {
        return new PlayerSession(this.player, this.flow, this.preview, this.token, now,
                this.submitting, new AtomicReference<>());
    }
}
