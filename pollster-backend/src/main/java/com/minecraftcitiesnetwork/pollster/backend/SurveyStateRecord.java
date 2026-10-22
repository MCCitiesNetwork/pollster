package com.minecraftcitiesnetwork.pollster.backend;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public record SurveyStateRecord(String surveyId, String contentHash, Instant openedAt, @Nullable Instant closedAt) {
}
