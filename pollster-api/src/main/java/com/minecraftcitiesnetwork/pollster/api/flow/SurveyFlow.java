package com.minecraftcitiesnetwork.pollster.api.flow;

import com.minecraftcitiesnetwork.pollster.api.condition.Condition;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionContext;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParseException;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParser;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * One player's walk through a survey: which question is next, what has been answered, and which
 * questions are hidden by their {@code show-if}. Holds no Bukkit types so it can be tested directly.
 */
public final class SurveyFlow {

    public static final int DEFAULT_TEXT_MAX = 255;

    private final SurveyDefinition survey;
    private final Predicate<String> hasPermission;
    private final Instant startedAt;
    private final int textMax;
    /** Parsed once; index matches the question list, null where a question has no condition. */
    private final List<@Nullable Condition> conditions;
    /** Raw answers by question key, including ones whose question is currently hidden until pruned. */
    private final Map<String, Answer> given = new HashMap<>();
    /** Index into the question list; equals the question count while on the review screen. */
    private int cursor;
    /** Counts every move (answer, skip, back) so a screen can tell it was built for an earlier step. */
    private int step;

    public SurveyFlow(SurveyDefinition survey, Predicate<String> hasPermission, Instant startedAt) {
        this(survey, hasPermission, startedAt, DEFAULT_TEXT_MAX);
    }

    public SurveyFlow(SurveyDefinition survey, Predicate<String> hasPermission, Instant startedAt, int textMax) {
        this.survey = survey;
        this.hasPermission = hasPermission;
        this.startedAt = startedAt;
        this.textMax = textMax;
        List<@Nullable Condition> parsed = new ArrayList<>();
        for (Question question : survey.questions()) {
            if (question.showIf() == null) {
                parsed.add(null);
                continue;
            }
            try {
                parsed.add(ConditionParser.parse(question.showIf()));
            } catch (ConditionParseException e) {
                // Validation rejects these before a survey can open, so reaching here is a bug.
                throw new IllegalArgumentException(
                        "Bad show-if on question " + question.key() + ": " + e.getMessage(), e);
            }
        }
        this.conditions = parsed;
        this.cursor = nextVisibleAtOrAfter(0);
    }

    public SurveyDefinition survey() {
        return this.survey;
    }

    public Instant startedAt() {
        return this.startedAt;
    }

    /** The question to show, or empty when every visible question is done and the review is next. */
    public Optional<Question> current() {
        if (this.cursor >= this.survey.questions().size()) {
            return Optional.empty();
        }
        return Optional.of(this.survey.questions().get(this.cursor));
    }

    public AnswerResult answer(Answer answer) {
        Optional<Question> current = current();
        if (current.isEmpty()) {
            return new AnswerResult.Invalid(AnswerResult.Reason.WRONG_TYPE);
        }
        if (answer instanceof Answer.Skipped) {
            return skip();
        }
        Question question = current.get();
        Answer stored = answer;
        if (!question.required() && isEmpty(answer)) {
            // An empty reply to an optional question (blank text, nothing ticked) means "nothing to
            // say". Storing it as a skip keeps it apart from a question that was hidden.
            stored = new Answer.Skipped();
        } else {
            AnswerResult.Reason problem = check(question, answer);
            if (problem != null) {
                return new AnswerResult.Invalid(problem);
            }
        }
        record(question, stored);
        return new AnswerResult.Accepted();
    }

    public AnswerResult skip() {
        Optional<Question> current = current();
        if (current.isEmpty()) {
            return new AnswerResult.Invalid(AnswerResult.Reason.WRONG_TYPE);
        }
        if (current.get().required()) {
            return new AnswerResult.Invalid(AnswerResult.Reason.NOT_SKIPPABLE);
        }
        record(current.get(), new Answer.Skipped());
        return new AnswerResult.Accepted();
    }

    /** Steps to the previous visible question; from review that is the last visible one. */
    public boolean back() {
        List<Boolean> visible = visibility();
        for (int i = this.cursor - 1; i >= 0; i--) {
            if (visible.get(i)) {
                this.cursor = i;
                this.step++;
                return true;
            }
        }
        return false;
    }

    /** 1-based position of the current question among visible ones; the count itself on review. */
    public int position() {
        List<Boolean> visible = visibility();
        int position = 0;
        for (int i = 0; i <= Math.min(this.cursor, visible.size() - 1); i++) {
            if (visible.get(i)) {
                position++;
            }
        }
        return position;
    }

    /**
     * How many moves the flow has made. A dialog captures it when built; a click that arrives after
     * another click already moved the flow sees a different value and must not act again.
     */
    public int step() {
        return this.step;
    }

    public int visibleCount() {
        return (int) visibility().stream().filter(Boolean::booleanValue).count();
    }

    /** Answers to visible questions only, in survey order. */
    public LinkedHashMap<String, Answer> answers() {
        List<Boolean> visible = visibility();
        LinkedHashMap<String, Answer> result = new LinkedHashMap<>();
        for (int i = 0; i < visible.size(); i++) {
            String key = this.survey.questions().get(i).key();
            Answer answer = this.given.get(key);
            if (visible.get(i) && answer != null) {
                result.put(key, answer);
            }
        }
        return result;
    }

    public Optional<Answer> answerFor(String key) {
        return Optional.ofNullable(answers().get(key));
    }

    private void record(Question question, Answer answer) {
        this.given.put(question.key(), answer);
        // Changing an earlier answer can hide later questions; their answers must not survive.
        List<Boolean> visible = visibility();
        for (int i = 0; i < visible.size(); i++) {
            if (!visible.get(i)) {
                this.given.remove(this.survey.questions().get(i).key());
            }
        }
        this.cursor = nextVisibleAtOrAfter(this.cursor + 1);
        this.step++;
    }

    private int nextVisibleAtOrAfter(int from) {
        List<Boolean> visible = visibility();
        for (int i = from; i < visible.size(); i++) {
            if (visible.get(i)) {
                return i;
            }
        }
        return this.survey.questions().size();
    }

    /** Recomputed from the start each time: a question sees only answers to visible earlier questions. */
    private List<Boolean> visibility() {
        List<Question> questions = this.survey.questions();
        List<Boolean> visible = new ArrayList<>(questions.size());
        Map<String, Answer> seen = new HashMap<>();
        ConditionContext context = new ConditionContext() {
            @Override
            public Optional<Answer> answer(String questionKey) {
                return Optional.ofNullable(seen.get(questionKey));
            }

            @Override
            public boolean hasPermission(String node) {
                return SurveyFlow.this.hasPermission.test(node);
            }
        };
        for (int i = 0; i < questions.size(); i++) {
            Condition condition = this.conditions.get(i);
            boolean shown = condition == null || condition.evaluate(context);
            visible.add(shown);
            Answer answer = this.given.get(questions.get(i).key());
            if (shown && answer != null) {
                seen.put(questions.get(i).key(), answer);
            }
        }
        return visible;
    }

    private @Nullable AnswerResult.Reason check(Question question, Answer answer) {
        return switch (question.type()) {
            case TEXT -> {
                if (!(answer instanceof Answer.Text text)) {
                    yield AnswerResult.Reason.WRONG_TYPE;
                }
                if (text.value().isBlank()) {
                    yield AnswerResult.Reason.REQUIRED;
                }
                yield text.value().length() > this.textMax ? AnswerResult.Reason.TOO_LONG : null;
            }
            case SINGLE, YES_NO -> {
                if (!(answer instanceof Answer.Choice choice)) {
                    yield AnswerResult.Reason.WRONG_TYPE;
                }
                yield optionKeys(question).contains(choice.optionKey()) ? null : AnswerResult.Reason.NOT_AN_OPTION;
            }
            case MULTIPLE -> {
                if (!(answer instanceof Answer.Choices choices)) {
                    yield AnswerResult.Reason.WRONG_TYPE;
                }
                if (!optionKeys(question).containsAll(choices.optionKeys())) {
                    yield AnswerResult.Reason.NOT_AN_OPTION;
                }
                int count = choices.optionKeys().size();
                if (count < question.min()) {
                    yield AnswerResult.Reason.TOO_FEW;
                }
                yield count > question.effectiveMax() ? AnswerResult.Reason.TOO_MANY : null;
            }
        };
    }

    private static boolean isEmpty(Answer answer) {
        return answer instanceof Answer.Text text && text.value().isBlank()
                || answer instanceof Answer.Choices choices && choices.optionKeys().isEmpty();
    }

    private static Set<String> optionKeys(Question question) {
        return question.options().stream().map(Option::key).collect(Collectors.toSet());
    }
}
