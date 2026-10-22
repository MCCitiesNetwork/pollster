package com.minecraftcitiesnetwork.pollster.api.event;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired after a response is saved and rewards have run. */
public final class SurveyCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SurveyDefinition survey;
    private final long responseId;

    public SurveyCompletedEvent(@NotNull Player player, @NotNull SurveyDefinition survey, long responseId) {
        this.player = player;
        this.survey = survey;
        this.responseId = responseId;
    }

    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull SurveyDefinition survey() {
        return this.survey;
    }

    public long responseId() {
        return this.responseId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
