package com.minecraftcitiesnetwork.pollster.api.report;

import java.time.Instant;

public record TextAnswer(String text, String playerName, Instant submittedAt) {
}
