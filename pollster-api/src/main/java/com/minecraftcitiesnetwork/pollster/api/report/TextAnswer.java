package com.minecraftcitiesnetwork.pollster.api.report;

import java.time.Instant;
import java.util.UUID;

/** @param player who answered; a report resolves the name only when the survey is not anonymous */
public record TextAnswer(String text, UUID player, Instant submittedAt) {
}
