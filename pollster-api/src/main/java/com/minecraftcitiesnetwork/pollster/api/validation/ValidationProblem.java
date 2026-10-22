package com.minecraftcitiesnetwork.pollster.api.validation;

import org.jetbrains.annotations.Nullable;

/** @param questionKey the question the problem belongs to, or null for a survey-level problem */
public record ValidationProblem(@Nullable String questionKey, String message) {
}
