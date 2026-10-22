package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.api.report.ReportData;
import com.minecraftcitiesnetwork.pollster.api.result.OpenResult;
import com.minecraftcitiesnetwork.pollster.api.result.SubmitResult;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Storage operations. All methods block; callers run them on the database executor. */
public interface PollsterBackend {

    OpenResult open(String surveyId, String contentHash, Instant now);

    void close(String surveyId, Instant now);

    void reopen(String surveyId);

    Optional<SurveyStateRecord> state(String surveyId);

    SubmitResult submit(ResponseSubmission submission);

    boolean hasResponded(String surveyId, UUID player);

    Set<String> completedSurveys(UUID player);

    Map<String, Integer> responseCounts();

    /** Deletes responses (their answers cascade) and the survey state. */
    void deleteSurveyData(String surveyId);

    Optional<Instant> lastFeatured(UUID player);

    void markFeatured(UUID player, Instant now);

    ReportData reportData(String surveyId);
}
