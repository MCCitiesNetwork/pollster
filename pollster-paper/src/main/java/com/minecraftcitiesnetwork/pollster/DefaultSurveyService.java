package com.minecraftcitiesnetwork.pollster;

import com.minecraftcitiesnetwork.pollster.api.flow.SurveyFlow;
import com.minecraftcitiesnetwork.pollster.api.hash.ContentHash;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.api.report.ReportBuilder;
import com.minecraftcitiesnetwork.pollster.api.report.SurveyReport;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;
import com.minecraftcitiesnetwork.pollster.api.validation.SurveyValidator;
import com.minecraftcitiesnetwork.pollster.api.validation.ValidationProblem;
import com.minecraftcitiesnetwork.pollster.backend.AnswerRowInput;
import com.minecraftcitiesnetwork.pollster.backend.PollsterBackend;
import com.minecraftcitiesnetwork.pollster.backend.ResponseSubmission;
import com.minecraftcitiesnetwork.pollster.backend.SurveyStateRecord;
import com.minecraftcitiesnetwork.pollster.store.SurveyStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Combines survey files with database state. Blocking work runs on the database executor; every
 * returned future completes on the main thread, which is also the only thread that touches the caches.
 */
public final class DefaultSurveyService implements SurveyService {

    private final SurveyStore store;
    private final PollsterBackend backend;
    private final ExecutorState exec;
    private final Logger log;
    // Replaced wholesale, so a reader never sees a half-updated map.
    private volatile Map<String, SurveyStateRecord> states = Map.of();
    private volatile Map<String, SurveyLock> locks = Map.of();

    public DefaultSurveyService(@NotNull SurveyStore store, @NotNull PollsterBackend backend,
                                @NotNull ExecutorState exec, @NotNull Logger log) {
        this.store = store;
        this.backend = backend;
        this.exec = exec;
        this.log = log;
    }

    /** Runs {@code work} on the database executor and hands the result back on the main thread. */
    private <T> CompletableFuture<T> onDatabase(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, this.exec.database())
                .thenApplyAsync(value -> value, this.exec.mainThread());
    }

    // ---- locks and cached state ----------------------------------------------------------

    @Override
    public @NotNull CompletableFuture<Map<String, SurveyLock>> refreshLocks() {
        return CompletableFuture.supplyAsync(this::loadStates, this.exec.database())
                .thenApplyAsync(this::applyStates, this.exec.mainThread());
    }

    /**
     * Blocking variant for plugin start-up, where the caller is the main thread and cannot wait on a
     * future that needs the main thread to complete.
     */
    public @NotNull Map<String, SurveyLock> refreshLocksBlocking() {
        return applyStates(loadStates());
    }

    private Map<String, SurveyStateRecord> loadStates() {
        Map<String, SurveyStateRecord> loaded = new HashMap<>();
        for (SurveyDefinition survey : this.store.all()) {
            if (survey.status() == SurveyStatus.DRAFT) {
                continue;
            }
            this.backend.state(survey.id()).ifPresent(state -> loaded.put(survey.id(), state));
        }
        return loaded;
    }

    private Map<String, SurveyLock> applyStates(Map<String, SurveyStateRecord> loaded) {
        this.states = Map.copyOf(loaded);
        Map<String, SurveyLock> computed = new HashMap<>();
        for (SurveyDefinition survey : this.store.all()) {
            SurveyLock lock = computeLock(survey);
            computed.put(survey.id(), lock);
            if (lock == SurveyLock.CONTENT_CHANGED) {
                this.log.warning("Survey '" + survey.id() + "' was edited after it was opened. "
                        + "It is locked until the edit is reverted or the survey is duplicated.");
            }
        }
        this.locks = Map.copyOf(computed);
        return this.locks;
    }

    private SurveyLock computeLock(SurveyDefinition survey) {
        if (survey.status() == SurveyStatus.DRAFT) {
            return SurveyLock.NOT_OPENED;
        }
        SurveyStateRecord state = this.states.get(survey.id());
        if (state == null) {
            return SurveyLock.NOT_OPENED;
        }
        return state.contentHash().equals(ContentHash.of(survey)) ? SurveyLock.OK : SurveyLock.CONTENT_CHANGED;
    }

    private void setState(String id, Optional<SurveyStateRecord> state) {
        Map<String, SurveyStateRecord> copy = new HashMap<>(this.states);
        state.ifPresentOrElse(value -> copy.put(id, value), () -> copy.remove(id));
        this.states = Map.copyOf(copy);
    }

    private void recomputeLock(String id) {
        Map<String, SurveyLock> copy = new HashMap<>(this.locks);
        this.store.get(id).ifPresentOrElse(survey -> copy.put(id, computeLock(survey)), () -> copy.remove(id));
        this.locks = Map.copyOf(copy);
    }

    @Override
    public @NotNull SurveyLock lock(@NotNull String surveyId) {
        return this.locks.getOrDefault(surveyId, SurveyLock.NOT_OPENED);
    }

    @Override
    public @NotNull Optional<SurveyStateRecord> cachedState(@NotNull String surveyId) {
        return Optional.ofNullable(this.states.get(surveyId));
    }

    // ---- reading -------------------------------------------------------------------------

    @Override
    public @NotNull Optional<SurveyDefinition> survey(@NotNull String surveyId) {
        return this.store.get(surveyId);
    }

    @Override
    public @NotNull List<SurveyDefinition> surveys() {
        return List.copyOf(this.store.all());
    }

    @Override
    public @NotNull List<SurveyDefinition> visibleTo(@NotNull Player player) {
        return this.store.all().stream()
                .filter(survey -> survey.status() == SurveyStatus.OPEN)
                .filter(survey -> lock(survey.id()) == SurveyLock.OK)
                .filter(survey -> !survey.requiresPermission()
                        || player.hasPermission("pollster.survey." + survey.id()))
                .toList();
    }

    // ---- lifecycle -----------------------------------------------------------------------

    @Override
    public @NotNull CompletableFuture<OpenResult> open(@NotNull String surveyId) {
        Optional<SurveyDefinition> found = this.store.get(surveyId);
        if (found.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No survey '" + surveyId + "'."));
        }
        SurveyDefinition survey = found.get();
        List<ValidationProblem> problems = SurveyValidator.validate(survey);
        if (!problems.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Survey '" + surveyId + "' cannot be opened: " + describe(problems)));
        }
        String hash = ContentHash.of(survey);
        return onDatabase(() -> {
            OpenResult result = this.backend.open(surveyId, hash, Instant.now());
            writeStatus(survey, SurveyStatus.OPEN);
            return new Opening(result, this.backend.state(surveyId));
        }).thenApply(opening -> {
            setState(surveyId, opening.state());
            recomputeLock(surveyId);
            return opening.result();
        });
    }

    private record Opening(OpenResult result, Optional<SurveyStateRecord> state) {
    }

    @Override
    public @NotNull CompletableFuture<Void> close(@NotNull String surveyId) {
        return changeStatus(surveyId, SurveyStatus.CLOSED, () -> this.backend.close(surveyId, Instant.now()));
    }

    @Override
    public @NotNull CompletableFuture<Void> reopen(@NotNull String surveyId) {
        return changeStatus(surveyId, SurveyStatus.OPEN, () -> this.backend.reopen(surveyId));
    }

    private CompletableFuture<Void> changeStatus(String surveyId, SurveyStatus status, Runnable databaseStep) {
        Optional<SurveyDefinition> found = this.store.get(surveyId);
        if (found.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No survey '" + surveyId + "'."));
        }
        SurveyDefinition survey = found.get();
        return onDatabase(() -> {
            databaseStep.run();
            writeStatus(survey, status);
            return this.backend.state(surveyId);
        }).thenAccept(state -> {
            setState(surveyId, state);
            recomputeLock(surveyId);
        });
    }

    private void writeStatus(SurveyDefinition survey, SurveyStatus status) {
        try {
            this.store.save(survey.withStatus(status));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @NotNull CompletableFuture<Void> save(@NotNull SurveyDefinition survey) {
        List<ValidationProblem> problems = SurveyValidator.validate(survey);
        if (!problems.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(describe(problems)));
        }
        return onDatabase(() -> {
            try {
                this.store.save(survey);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return (Void) null;
        }).thenAccept(ignored -> recomputeLock(survey.id()));
    }

    @Override
    public @NotNull CompletableFuture<SurveyDefinition> duplicate(@NotNull String sourceId, @NotNull String newId) {
        Optional<SurveyDefinition> source = this.store.get(sourceId);
        if (source.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No survey '" + sourceId + "'."));
        }
        if (!SurveyIds.isValidSlug(newId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Survey id must be 3 to 48 characters of a-z, 0-9 and '-'."));
        }
        if (this.store.exists(newId)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "A survey with id '" + newId + "' already exists."));
        }
        SurveyDefinition s = source.get();
        SurveyDefinition copy = new SurveyDefinition(newId, s.name(), s.description(), SurveyStatus.DRAFT,
                s.anonymous(), s.featured(), s.requiresPermission(), s.rewards(), s.questions());
        return onDatabase(() -> {
            try {
                this.store.save(copy);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return copy;
        }).thenApply(saved -> {
            recomputeLock(newId);
            return saved;
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> delete(@NotNull String surveyId) {
        return onDatabase(() -> {
            this.backend.deleteSurveyData(surveyId);
            try {
                this.store.delete(surveyId);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return (Void) null;
        }).thenAccept(ignored -> {
            setState(surveyId, Optional.empty());
            recomputeLock(surveyId);
        });
    }

    // ---- responses -----------------------------------------------------------------------

    @Override
    public @NotNull CompletableFuture<SubmitResult> submit(@NotNull SurveyFlow flow, @NotNull UUID player,
                                                           @NotNull String playerName) {
        SurveyDefinition survey = flow.survey();
        if (lock(survey.id()) != SurveyLock.OK) {
            return CompletableFuture.completedFuture(new SubmitResult.ContentChanged());
        }
        ResponseSubmission submission = new ResponseSubmission(survey.id(), ContentHash.of(survey), player,
                playerName, flow.startedAt(), Instant.now(), AnswerRowInput.fromAnswers(flow.answers()));
        return onDatabase(() -> this.backend.submit(submission));
    }

    @Override
    public @NotNull CompletableFuture<Set<String>> completed(@NotNull UUID player) {
        return onDatabase(() -> this.backend.completedSurveys(player));
    }

    @Override
    public @NotNull CompletableFuture<Map<String, Integer>> responseCounts() {
        return onDatabase(this.backend::responseCounts);
    }

    @Override
    public @NotNull CompletableFuture<SurveyReport> report(@NotNull String surveyId) {
        Optional<SurveyDefinition> found = this.store.get(surveyId);
        if (found.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No survey '" + surveyId + "'."));
        }
        return onDatabase(() -> ReportBuilder.build(found.get(), this.backend.reportData(surveyId)));
    }

    @Override
    public @NotNull CompletableFuture<Optional<Instant>> lastFeatured(@NotNull UUID player) {
        return onDatabase(() -> this.backend.lastFeatured(player));
    }

    @Override
    public @NotNull CompletableFuture<Void> markFeatured(@NotNull UUID player) {
        return onDatabase(() -> {
            this.backend.markFeatured(player, Instant.now());
            return (Void) null;
        });
    }

    private static String describe(List<ValidationProblem> problems) {
        return problems.stream()
                .map(p -> p.questionKey() == null ? p.message()
                        : "Question \"" + p.questionKey() + "\": " + p.message())
                .collect(Collectors.joining("; "));
    }
}
