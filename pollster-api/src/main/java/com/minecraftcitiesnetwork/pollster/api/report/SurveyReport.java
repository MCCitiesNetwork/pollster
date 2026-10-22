package com.minecraftcitiesnetwork.pollster.api.report;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * @param playerNames display name for each text-answer author; empty when the survey is anonymous,
 *                    so names never reach a report that must not show them
 */
public record SurveyReport(SurveyDefinition survey, int participants, OptionalDouble averageSeconds,
                           List<QuestionReport> questions, Map<UUID, String> playerNames) {

    public SurveyReport {
        playerNames = Map.copyOf(playerNames);
    }

    /** A report without names, as built from storage before any lookup. */
    public SurveyReport(SurveyDefinition survey, int participants, OptionalDouble averageSeconds,
                        List<QuestionReport> questions) {
        this(survey, participants, averageSeconds, questions, Map.of());
    }

    public SurveyReport withPlayerNames(Map<UUID, String> names) {
        return new SurveyReport(this.survey, this.participants, this.averageSeconds, this.questions, names);
    }
}
