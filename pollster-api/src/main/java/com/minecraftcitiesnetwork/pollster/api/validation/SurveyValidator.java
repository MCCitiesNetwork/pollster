package com.minecraftcitiesnetwork.pollster.api.validation;

import com.minecraftcitiesnetwork.pollster.api.condition.Condition;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParseException;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParser;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.QuestionType;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural rules plus the references and syntax of every {@code show-if} condition. */
public final class SurveyValidator {

    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 50;

    // Words the condition parser reserves, so a question key equal to one could never be referenced.
    private static final Set<String> CONDITION_KEYWORDS =
            Set.of("and", "or", "not", "in", "has", "answered", "answer", "permission");

    private SurveyValidator() {
    }

    public static List<ValidationProblem> validate(SurveyDefinition survey) {
        List<ValidationProblem> problems = new ArrayList<>();
        if (!SurveyIds.isValidSlug(survey.id())) {
            problems.add(new ValidationProblem(null,
                    "Survey id must be 3 to 48 characters of a-z, 0-9 and '-': \"" + survey.id() + "\"."));
        }

        Set<String> seenQuestionKeys = new HashSet<>();
        List<Question> questions = survey.questions();
        for (int index = 0; index < questions.size(); index++) {
            Question question = questions.get(index);
            if (CONDITION_KEYWORDS.contains(question.key())) {
                problems.add(new ValidationProblem(question.key(),
                        "Question key \"" + question.key() + "\" is a condition keyword; pick another."));
            }
            if (!SurveyIds.isValidKey(question.key())) {
                problems.add(new ValidationProblem(question.key(),
                        "Question key must be 1 to 48 characters of a-z, 0-9 and '-'."));
            }
            if (!seenQuestionKeys.add(question.key())) {
                problems.add(new ValidationProblem(question.key(), "Duplicate question key."));
            }
            validateQuestion(question, problems);
            validateCondition(survey, index, problems);
        }
        return problems;
    }

    private static void validateCondition(SurveyDefinition survey, int index, List<ValidationProblem> problems) {
        Question question = survey.questions().get(index);
        if (question.showIf() == null) {
            return;
        }
        Condition condition;
        try {
            condition = ConditionParser.parse(question.showIf());
        } catch (ConditionParseException e) {
            problems.add(new ValidationProblem(question.key(), "Invalid show-if: " + e.getMessage()));
            return;
        }
        for (Map.Entry<String, Set<String>> reference : condition.references().entrySet()) {
            String referencedKey = reference.getKey();
            int referencedIndex = indexOf(survey, referencedKey);
            if (referencedIndex < 0) {
                problems.add(new ValidationProblem(question.key(),
                        "show-if refers to unknown question \"" + referencedKey + "\"."));
                continue;
            }
            if (referencedIndex >= index) {
                problems.add(new ValidationProblem(question.key(),
                        "show-if refers to \"" + referencedKey + "\", which must come before this question."));
                continue;
            }
            Question referenced = survey.questions().get(referencedIndex);
            if (referenced.type() != QuestionType.TEXT) {
                Set<String> optionKeys = new HashSet<>();
                referenced.options().forEach(option -> optionKeys.add(option.key()));
                for (String value : reference.getValue()) {
                    if (!optionKeys.contains(value)) {
                        problems.add(new ValidationProblem(question.key(), "show-if compares \"" + referencedKey
                                + "\" with \"" + value + "\", which is not one of its options."));
                    }
                }
            }
        }
        checkHasUsage(survey, condition, question, problems);
    }

    private static void checkHasUsage(
            SurveyDefinition survey, Condition condition, Question owner, List<ValidationProblem> problems) {
        switch (condition) {
            case Condition.And and -> {
                checkHasUsage(survey, and.l(), owner, problems);
                checkHasUsage(survey, and.r(), owner, problems);
            }
            case Condition.Or or -> {
                checkHasUsage(survey, or.l(), owner, problems);
                checkHasUsage(survey, or.r(), owner, problems);
            }
            case Condition.Not not -> checkHasUsage(survey, not.c(), owner, problems);
            case Condition.Has has -> survey.question(has.question())
                    .filter(target -> target.type() != QuestionType.MULTIPLE)
                    .ifPresent(target -> problems.add(new ValidationProblem(owner.key(),
                            "show-if uses has on \"" + target.key() + "\" (column " + has.column()
                                    + "); has only works on multiple-choice questions.")));
            default -> {
            }
        }
    }

    private static int indexOf(SurveyDefinition survey, String key) {
        for (int i = 0; i < survey.questions().size(); i++) {
            if (survey.questions().get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
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
