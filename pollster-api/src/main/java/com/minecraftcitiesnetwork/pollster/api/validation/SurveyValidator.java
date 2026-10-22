package com.minecraftcitiesnetwork.pollster.api.validation;

import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Structural rules only; conditions are checked separately. */
public final class SurveyValidator {

    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 50;

    private SurveyValidator() {
    }

    public static List<ValidationProblem> validate(SurveyDefinition survey) {
        List<ValidationProblem> problems = new ArrayList<>();
        if (!SurveyIds.isValidSlug(survey.id())) {
            problems.add(new ValidationProblem(null,
                    "Survey id must be 3 to 48 characters of a-z, 0-9 and '-': \"" + survey.id() + "\"."));
        }

        Set<String> seenQuestionKeys = new HashSet<>();
        for (Question question : survey.questions()) {
            if (!SurveyIds.isValidKey(question.key())) {
                problems.add(new ValidationProblem(question.key(),
                        "Question key must be 1 to 48 characters of a-z, 0-9 and '-'."));
            }
            if (!seenQuestionKeys.add(question.key())) {
                problems.add(new ValidationProblem(question.key(), "Duplicate question key."));
            }
            validateQuestion(question, problems);
        }
        return problems;
    }

    private static void validateQuestion(Question question, List<ValidationProblem> problems) {
        switch (question.type()) {
            case SINGLE -> validateOptions(question, problems);
            case MULTIPLE -> {
                validateOptions(question, problems);
                validateSelectionRange(question, problems);
            }
            case TEXT, YES_NO -> {
                // Nothing further: TEXT has no options and YES_NO's are fixed.
            }
        }
    }

    private static void validateOptions(Question question, List<ValidationProblem> problems) {
        int count = question.options().size();
        if (count < MIN_OPTIONS || count > MAX_OPTIONS) {
            problems.add(new ValidationProblem(question.key(),
                    "A choice question needs " + MIN_OPTIONS + " to " + MAX_OPTIONS + " options, found " + count + "."));
        }
        Set<String> seen = new HashSet<>();
        for (Option option : question.options()) {
            if (!SurveyIds.isValidKey(option.key())) {
                problems.add(new ValidationProblem(question.key(),
                        "Option key must be 1 to 48 characters of a-z, 0-9 and '-': \"" + option.key() + "\"."));
            }
            if (!seen.add(option.key())) {
                problems.add(new ValidationProblem(question.key(), "Duplicate option key \"" + option.key() + "\"."));
            }
        }
    }

    private static void validateSelectionRange(Question question, List<ValidationProblem> problems) {
        int max = question.effectiveMax();
        int count = question.options().size();
        if (question.min() < 0 || question.min() > max) {
            problems.add(new ValidationProblem(question.key(),
                    "Minimum selections (" + question.min() + ") must be between 0 and the maximum (" + max + ")."));
        }
        if (max > count) {
            problems.add(new ValidationProblem(question.key(),
                    "Maximum selections (" + max + ") cannot exceed the option count (" + count + ")."));
        }
        if (question.required() && question.min() == 0) {
            problems.add(new ValidationProblem(question.key(),
                    "A required question needs a minimum of at least 1; allow 0 only on optional questions."));
        }
    }
}
