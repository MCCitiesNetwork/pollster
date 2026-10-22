package com.minecraftcitiesnetwork.pollster.api.report;

import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;

import java.util.List;
import java.util.OptionalDouble;

public record SurveyReport(SurveyDefinition survey, int participants, OptionalDouble averageSeconds,
                           List<QuestionReport> questions) {
}
