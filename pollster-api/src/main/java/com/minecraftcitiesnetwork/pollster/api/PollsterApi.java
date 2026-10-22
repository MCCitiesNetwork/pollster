package com.minecraftcitiesnetwork.pollster.api;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Read-only access for other plugins, registered with the server's services manager. */
public interface PollsterApi {

    @NotNull Collection<SurveyDefinition> surveys();

    @NotNull Optional<SurveyDefinition> survey(@NotNull String id);

    @NotNull CompletableFuture<Boolean> hasCompleted(@NotNull String id, @NotNull UUID player);
}
