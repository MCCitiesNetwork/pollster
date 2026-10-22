package com.minecraftcitiesnetwork.pollster.api.event;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread before a response is saved. Cancelling discards the submission. */
public final class SurveySubmitEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SurveyDefinition survey;
    private final java.util.Map<String, Answer> answers;

    public SurveySubmitEvent(@NotNull Player player, @NotNull SurveyDefinition survey, @NotNull java.util.Map<String, Answer> answers) {
        this.player = player;
        this.survey = survey;
        this.answers = answers;
    }

    public @NotNull Player player() {
        return this.player;
    }

    public @NotNull SurveyDefinition survey() {
        return this.survey;
    }

    public @NotNull java.util.Map<String, Answer> answers() {
        return this.answers;
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
