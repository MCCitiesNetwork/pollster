package com.minecraftcitiesnetwork.pollster.api.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * One question in a survey.
 *
 * @param min only meaningful for {@link QuestionType#MULTIPLE}
 * @param max only meaningful for {@link QuestionType#MULTIPLE}; 0 means "the option count"
 */
public record Question(
        String key,
        QuestionType type,
        String text,
        List<Option> options,
        boolean required,
        int min,
        int max,
        @Nullable String showIf) {

    private static final List<Option> YES_NO_OPTIONS = List.of(new Option("yes", "Yes"), new Option("no", "No"));

    public Question {
        // YES_NO always stores the keys yes and no, so whatever a file or editor supplies is ignored.
        options = type == QuestionType.YES_NO ? YES_NO_OPTIONS : List.copyOf(options);
    }

    /** The largest number of selections allowed, with the "0 means option count" default resolved. */
    public int effectiveMax() {
        return this.max == 0 ? this.options.size() : this.max;
    }
}
