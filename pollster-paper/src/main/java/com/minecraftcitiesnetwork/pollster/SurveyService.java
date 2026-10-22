package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The one place that combines survey files with database state. Screens and commands talk to
 * this, never to the store or backend directly, so the content lock is enforced in one place.
 *
 * <p>Every returned future completes on the server's main thread.
 */
public interface SurveyService {

    /** Result of comparing a survey file's content hash with the hash stored when it was opened. */
    enum SurveyLock { OK, CONTENT_CHANGED, NOT_OPENED }

    /** Re-reads database state for every OPEN or CLOSED survey and recomputes each lock. */
    @NotNull CompletableFuture<Map<String, SurveyLock>> refreshLocks();

    /** The cached lock from the last {@link #refreshLocks()}; {@code NOT_OPENED} for drafts. */
    @NotNull SurveyLock lock(@NotNull String surveyId);

    /** Database state cached by the last refresh, kept current by open/close/reopen. */
    @NotNull Optional<SurveyStateRecord> cachedState(@NotNull String surveyId);

    @NotNull Optional<SurveyDefinition> survey(@NotNull String surveyId);

    /** All surveys, sorted by id. */
    @NotNull List<SurveyDefinition> surveys();

    /** OPEN surveys with lock OK that the player may see (permission-gated ones need pollster.survey.&lt;id&gt;). */
    @NotNull List<SurveyDefinition> visibleTo(@NotNull Player player);

    /** Validates, writes status OPEN to the file and stores the content hash. Fails with IllegalStateException when invalid. */
    @NotNull CompletableFuture<OpenResult> open(@NotNull String surveyId);

    @NotNull CompletableFuture<Void> close(@NotNull String surveyId);

    @NotNull CompletableFuture<Void> reopen(@NotNull String surveyId);

    /** Saves a survey definition (editor changes). Fails with IllegalArgumentException listing validation problems. */
    @NotNull CompletableFuture<Void> save(@NotNull SurveyDefinition survey);

    /** Copies any survey into a new DRAFT under {@code newId}, with no database state. */
    @NotNull CompletableFuture<SurveyDefinition> duplicate(@NotNull String sourceId, @NotNull String newId);

    /** Deletes the file and every response and state row for the survey. */
    @NotNull CompletableFuture<Void> delete(@NotNull String surveyId);

    /** Saves a finished flow. Returns ContentChanged without touching the database when the lock is not OK. */
    @NotNull CompletableFuture<SubmitResult> submit(@NotNull SurveyFlow flow, @NotNull UUID player, @NotNull String playerName);

    @NotNull CompletableFuture<Set<String>> completed(@NotNull UUID player);

    @NotNull CompletableFuture<Map<String, Integer>> responseCounts();

    @NotNull CompletableFuture<SurveyReport> report(@NotNull String surveyId);

    @NotNull CompletableFuture<Optional<java.time.Instant>> lastFeatured(@NotNull UUID player);

    @NotNull CompletableFuture<Void> markFeatured(@NotNull UUID player);
}
