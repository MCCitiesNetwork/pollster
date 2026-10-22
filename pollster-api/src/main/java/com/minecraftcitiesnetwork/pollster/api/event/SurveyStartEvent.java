package com.minecraftcitiesnetwork.pollster.api.event;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired when a player starts a survey (never in preview). Cancelling stops the survey opening. */
public final class SurveyStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SurveyDefinition survey;

    public SurveyStartEvent(@NotNull Player player, @NotNull SurveyDefinition survey) {
        this.player = player;
        this.survey = survey;
    }

    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull SurveyDefinition survey() {
        return this.survey;
    }

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
