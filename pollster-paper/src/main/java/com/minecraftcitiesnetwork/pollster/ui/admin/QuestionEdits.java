package com.minecraftcitiesnetwork.pollster.ui.admin;

import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParseException;
import com.minecraftcitiesnetwork.pollster.api.condition.ConditionParser;
import com.minecraftcitiesnetwork.pollster.api.model.Option;
import com.minecraftcitiesnetwork.pollster.api.model.Question;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyDefinition;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyIds;
import com.minecraftcitiesnetwork.pollster.api.model.SurveyStatus;
import com.minecraftcitiesnetwork.pollster.config.MessageKeys;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure edits of a survey's question list. Each returns a new survey or the reason the edit is
 * refused, so the screens can show the reason and the rules stay testable without a dialog.
 */
public final class QuestionEdits {

    public sealed interface EditResult permits Ok, Rejected {
    }

    public record Ok(@NotNull SurveyDefinition s) implements EditResult {
    }

    /** @param messageKey a key under {@code editor.*} in messages.yml; {@code <args>} receives {@code args} joined */
    public record Rejected(@NotNull String messageKey, @NotNull List<String> args) implements EditResult {
        public Rejected {
            args = List.copyOf(args);
        }
    }

    private QuestionEdits() {
    }

    public static @NotNull EditResult add(@NotNull SurveyDefinition s, @NotNull Question q) {
        if (s.status() != SurveyStatus.DRAFT) {
            return locked();
        }
        if (s.question(q.key()).isPresent()) {
            return new Rejected(MessageKeys.EDITOR_DUPLICATE_KEY, List.of(q.key()));
        }
        List<Question> questions = new ArrayList<>(s.questions());
        questions.add(q);
        return new Ok(s.withQuestions(questions));
    }

    public static @NotNull EditResult replace(@NotNull SurveyDefinition s, @NotNull String key, @NotNull Question q) {
        if (s.status() != SurveyStatus.DRAFT) {
            return locked();
        }
        int index = indexOf(s, key);
        if (index < 0) {
            return new Rejected(MessageKeys.EDITOR_UNKNOWN_QUESTION, List.of(key));
        }
        if (!key.equals(q.key())) {
            if (s.question(q.key()).isPresent()) {
                return new Rejected(MessageKeys.EDITOR_DUPLICATE_KEY, List.of(q.key()));
            }
            List<String> referrers = referrers(s, key);
            if (!referrers.isEmpty()) {
                return new Rejected(MessageKeys.EDITOR_KEY_REFERENCED, referrers);
            }
        }
        List<Question> questions = new ArrayList<>(s.questions());
        questions.set(index, q);
        return new Ok(s.withQuestions(questions));
    }

    /** Moves a question by {@code delta} places; moving past either end changes nothing. */
    public static @NotNull EditResult move(@NotNull SurveyDefinition s, @NotNull String key, int delta) {
        if (s.status() != SurveyStatus.DRAFT) {
            return locked();
        }
        int index = indexOf(s, key);
        if (index < 0) {
            return new Rejected(MessageKeys.EDITOR_UNKNOWN_QUESTION, List.of(key));
        }
        int target = index + delta;
        if (target < 0 || target >= s.questions().size() || target == index) {
            return new Ok(s);
        }
        List<Question> questions = new ArrayList<>(s.questions());
        Question moved = questions.remove(index);
        questions.add(target, moved);
        SurveyDefinition result = s.withQuestions(questions);
        // A condition may only look backwards, so every question must still come after what it refers to.
        for (int i = 0; i < questions.size(); i++) {
            for (String referenced : referencedKeys(questions.get(i))) {
                int referencedIndex = indexOf(result, referenced);
                if (referencedIndex >= i) {
                    return new Rejected(MessageKeys.EDITOR_MOVE_BREAKS_CONDITION,
                            List.of(questions.get(i).key(), referenced));
                }
            }
        }
        return new Ok(result);
    }

    public static @NotNull EditResult remove(@NotNull SurveyDefinition s, @NotNull String key) {
        if (s.status() != SurveyStatus.DRAFT) {
            return locked();
        }
        if (s.question(key).isEmpty()) {
            return new Rejected(MessageKeys.EDITOR_UNKNOWN_QUESTION, List.of(key));
        }
        List<String> referrers = referrers(s, key);
        if (!referrers.isEmpty()) {
            return new Rejected(MessageKeys.EDITOR_REMOVE_REFERENCED, referrers);
        }
        List<Question> questions = new ArrayList<>(s.questions());
        questions.removeIf(question -> question.key().equals(key));
        return new Ok(s.withQuestions(questions));
    }

    /**
     * One option per non-blank line, as {@code key: Label} or just {@code Label}. A prefix before
     * the colon only counts as a key when it is a valid key, so a label such as "Note: this" stays whole.
     */
    public static @NotNull List<Option> bulkParse(@NotNull String text) {
        List<Option> options = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String key = null;
            String label = line;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String prefix = line.substring(0, colon).strip();
                String rest = line.substring(colon + 1).strip();
                if (SurveyIds.isValidKey(prefix) && !rest.isEmpty()) {
                    key = prefix;
                    label = rest;
                }
            }
            options.add(new Option(uniqueKey(key == null ? SurveyIds.slugify(label) : key, used), label));
        }
        return options;
    }

    /** The key made unique among {@code used} by appending -2, -3 and so on, and recorded there. */
    static @NotNull String uniqueKey(@NotNull String base, @NotNull Set<String> used) {
        String candidate = base;
        for (int n = 2; !used.add(candidate); n++) {
            String suffix = "-" + n;
            int room = SurveyIds.MAX_LENGTH - suffix.length();
            candidate = (base.length() > room ? base.substring(0, room) : base) + suffix;
        }
        return candidate;
    }

    /** The question with a new option list, keeping the selection limits inside the new option count. */
    static @NotNull Question withOptions(@NotNull Question q, @NotNull List<Option> options) {
        int max = q.max() > options.size() ? 0 : q.max();
        int effectiveMax = max == 0 ? options.size() : max;
        int min = Math.min(q.min(), effectiveMax);
        return new Question(q.key(), q.type(), q.text(), options, q.required(), min, max, q.showIf());
    }

    /** Keys of questions whose condition refers to {@code key}. */
    static @NotNull List<String> referrers(@NotNull SurveyDefinition s, @NotNull String key) {
        List<String> result = new ArrayList<>();
        for (Question question : s.questions()) {
            if (referencedKeys(question).contains(key)) {
                result.add(question.key());
            }
        }
        return result;
    }

    private static Set<String> referencedKeys(Question question) {
        if (question.showIf() == null) {
            return Set.of();
        }
        try {
            return ConditionParser.parse(question.showIf()).references().keySet();
        } catch (ConditionParseException e) {
            // A malformed condition is reported by the validator on save; it refers to nothing here.
            return Set.of();
        }
    }

    private static int indexOf(SurveyDefinition s, String key) {
        for (int i = 0; i < s.questions().size(); i++) {
            if (s.questions().get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static Rejected locked() {
        return new Rejected(MessageKeys.EDITOR_LOCKED, List.of());
    }
}
