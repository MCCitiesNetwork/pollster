package com.minecraftcitiesnetwork.pollster.backend;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResponseSubmission(String surveyId, String contentHash, UUID player, Instant startedAt,
                                 Instant submittedAt, List<AnswerRowInput> answers) {
}
