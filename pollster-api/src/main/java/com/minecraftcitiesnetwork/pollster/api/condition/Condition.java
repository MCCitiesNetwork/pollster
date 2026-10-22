package com.minecraftcitiesnetwork.pollster.api.condition;

import java.util.List;

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
