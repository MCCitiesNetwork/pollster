package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.PollsterApi;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

/** The read-only view other plugins get from the services manager. */
public final class PollsterApiImpl implements PollsterApi {

    private final SurveyService service;

    public PollsterApiImpl(@NotNull SurveyService service) {
        this.service = service;
    }

    @Override
    public @NotNull Collection<SurveyDefinition> surveys() {
        return this.service.surveys();
    }

    @Override
    public @NotNull Optional<SurveyDefinition> survey(@NotNull String id) {
        return this.service.survey(id);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> hasCompleted(@NotNull String id, @NotNull UUID player) {
        return this.service.completed(player).thenApply(done -> done.contains(id));
    }
}
