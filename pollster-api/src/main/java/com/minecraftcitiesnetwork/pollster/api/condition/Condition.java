package com.minecraftcitiesnetwork.pollster.api.condition;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A parsed {@code show-if} expression. The leaf records carry the 1-based column of their
 * {@code answer(} or {@code permission(} token so that later checks, such as an unknown question
 * key, can point at the exact place in the source.
 */
public sealed interface Condition
        permits Condition.And,
                Condition.Or,
                Condition.Not,
                Condition.Equals,
                Condition.NotEquals,
                Condition.In,
                Condition.Has,
                Condition.Answered,
                Condition.HasPermission {

    /**
     * Truth table: a {@link Answer.Skipped} or missing answer counts as unanswered, so {@code ==},
     * {@code in}, {@code has} and {@code !=} are all false for it. Text compares ignoring case; option
     * keys compare exactly.
     */
    default boolean evaluate(ConditionContext ctx) {
        return switch (this) {
            case And and -> and.l().evaluate(ctx) && and.r().evaluate(ctx);
            case Or or -> or.l().evaluate(ctx) || or.r().evaluate(ctx);
            case Not not -> !not.c().evaluate(ctx);
            case Equals eq -> single(ctx, eq.question()).map(v -> v.matches(eq.value())).orElse(false);
            case NotEquals ne -> single(ctx, ne.question()).map(v -> !v.matches(ne.value())).orElse(false);
            case In in -> single(ctx, in.question())
                    .map(v -> in.values().stream().anyMatch(v::matches))
                    .orElse(false);
            case Has has -> ctx.answer(has.question())
                    .map(a -> a instanceof Answer.Choices c && c.optionKeys().contains(has.value()))
                    .orElse(false);
            case Answered answered -> answered(ctx, answered.question());
            case HasPermission perm -> ctx.hasPermission(perm.node());
        };
    }

    /** Question key to the option values compared against it; empty set for {@code answered}. */
    default Map<String, Set<String>> references() {
        Map<String, Set<String>> result = new HashMap<>();
        collectReferences(this, result);
        return result;
    }

    private static void collectReferences(Condition c, Map<String, Set<String>> out) {
        switch (c) {
            case And and -> {
                collectReferences(and.l(), out);
                collectReferences(and.r(), out);
            }
            case Or or -> {
                collectReferences(or.l(), out);
                collectReferences(or.r(), out);
            }
            case Not not -> collectReferences(not.c(), out);
            case Equals eq -> add(out, eq.question(), eq.value());
            case NotEquals ne -> add(out, ne.question(), ne.value());
            case In in -> in.values().forEach(v -> add(out, in.question(), v));
            case Has has -> add(out, has.question(), has.value());
            case Answered answered -> out.computeIfAbsent(answered.question(), k -> new HashSet<>());
            case HasPermission ignored -> {
            }
        }
    }

    private static void add(Map<String, Set<String>> out, String question, String value) {
        out.computeIfAbsent(question, k -> new HashSet<>()).add(value);
    }

    private static boolean answered(ConditionContext ctx, String question) {
        return ctx.answer(question).filter(a -> !(a instanceof Answer.Skipped)).isPresent();
    }

    /** A comparable single value (choice key or text), or empty when unanswered, skipped or multi-choice. */
    private static Optional<SingleValue> single(ConditionContext ctx, String question) {
        return ctx.answer(question).flatMap(a -> switch (a) {
            case Answer.Choice choice -> Optional.of(new SingleValue(choice.optionKey(), false));
            case Answer.Text text -> Optional.of(new SingleValue(text.value(), true));
            case Answer.Choices ignored -> Optional.empty();
            case Answer.Skipped ignored -> Optional.empty();
        });
    }

    /** Text answers ignore case; option keys do not. */
    record SingleValue(String value, boolean ignoreCase) {
        boolean matches(String other) {
            return this.ignoreCase ? this.value.equalsIgnoreCase(other) : this.value.equals(other);
        }
    }

    record And(Condition l, Condition r) implements Condition {}

    record Or(Condition l, Condition r) implements Condition {}

    record Not(Condition c) implements Condition {}

    record Equals(String question, String value, int column) implements Condition {}

    record NotEquals(String question, String value, int column) implements Condition {}

    record In(String question, List<String> values, int column) implements Condition {
        public In {
            // Defensive copy so a parsed condition stays immutable.
            values = List.copyOf(values);
        }
    }

    record Has(String question, String value, int column) implements Condition {}

    record Answered(String question, int column) implements Condition {}

    record HasPermission(String node, int column) implements Condition {}
}
