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

    /**
     * Re-reads database state for every survey and recomputes each lock. A survey with a state
     * row is checked whatever its file status says; one without is NOT_OPENED.
     */
    @NotNull CompletableFuture<Map<String, SurveyLock>> refreshLocks();

    /** The cached lock from the last {@link #refreshLocks()}; {@code NOT_OPENED} without a state row. */
    @NotNull SurveyLock lock(@NotNull String surveyId);

    /** Database state cached by the last refresh, kept current by open/close/reopen. */
    @NotNull Optional<SurveyStateRecord> cachedState(@NotNull String surveyId);

    @NotNull Optional<SurveyDefinition> survey(@NotNull String surveyId);

    /** All surveys, sorted by id. */
    @NotNull List<SurveyDefinition> surveys();

    /** OPEN surveys with lock OK that the player may see (permission-gated ones need pollster.survey.&lt;id&gt;). */
    @NotNull List<SurveyDefinition> visibleTo(@NotNull Player player);

    /**
     * Validates, writes status OPEN to the file and stores the content hash. Fails with
     * IllegalStateException when invalid or not a DRAFT.
     */
    @NotNull CompletableFuture<OpenResult> open(@NotNull String surveyId);

    /** OPEN to CLOSED. Fails with IllegalStateException when the survey is not OPEN. */
    @NotNull CompletableFuture<Void> close(@NotNull String surveyId);

    /** CLOSED to OPEN. Fails with IllegalStateException when the survey is not CLOSED. */
    @NotNull CompletableFuture<Void> reopen(@NotNull String surveyId);

    /**
     * Saves editor changes to an existing survey. Fails with IllegalArgumentException listing
     * validation problems, and with {@link SurveyLifecycleException} when the save would break the
     * lifecycle: a status change, a content change (questions or anonymity) on an OPEN survey,
     * any change to a CLOSED survey, or a survey whose file no longer exists (so a stale dialog
     * cannot bring back a deleted survey).
     */
    @NotNull CompletableFuture<Void> save(@NotNull SurveyDefinition survey);

    /**
     * Writes a new DRAFT survey. Fails with IllegalArgumentException for validation problems or a
     * taken id, and with IllegalStateException when the survey is not a DRAFT.
     */
    @NotNull CompletableFuture<Void> create(@NotNull SurveyDefinition survey);

    /** Copies any survey into a new DRAFT under {@code newId}, with no database state. */
    @NotNull CompletableFuture<SurveyDefinition> duplicate(@NotNull String sourceId, @NotNull String newId);

    /** Deletes the file and every response and state row for the survey. */
    @NotNull CompletableFuture<Void> delete(@NotNull String surveyId);

    /** Saves a finished flow. Returns ContentChanged without touching the database when the lock is not OK. */
    @NotNull CompletableFuture<SubmitResult> submit(@NotNull SurveyFlow flow, @NotNull UUID player, @NotNull String playerName);

    @NotNull CompletableFuture<Set<String>> completed(@NotNull UUID player);

    @NotNull CompletableFuture<Map<String, Integer>> responseCounts();

    /** The report. When the lock is not OK, the report's survey is anonymous whatever the file says. */
    @NotNull CompletableFuture<SurveyReport> report(@NotNull String surveyId);

    @NotNull CompletableFuture<Optional<java.time.Instant>> lastFeatured(@NotNull UUID player);

    @NotNull CompletableFuture<Void> markFeatured(@NotNull UUID player);
}
