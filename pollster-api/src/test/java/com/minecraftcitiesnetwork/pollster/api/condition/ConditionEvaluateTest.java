package com.minecraftcitiesnetwork.pollster.api.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.minecraftcitiesnetwork.pollster.api.flow.Answer;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConditionEvaluateTest {

    private static ConditionContext ctx(Map<String, Answer> answers, Set<String> permissions) {
        return new ConditionContext() {
            @Override
            public Optional<Answer> answer(String questionKey) {
                return Optional.ofNullable(answers.get(questionKey));
            }

            @Override
            public boolean hasPermission(String node) {
                return permissions.contains(node);
            }
        };
    }

    private static ConditionContext ctx(Map<String, Answer> answers) {
        return ctx(answers, Set.of());
    }

    private static boolean eval(String expression, ConditionContext ctx) throws ConditionParseException {
        return ConditionParser.parse(expression).evaluate(ctx);
    }

    @Test
    void evaluate_equalsChoice() throws Exception {
        ConditionContext ctx = ctx(Map.of("party", new Answer.Choice("ger")));
        assertTrue(eval("answer(party) == \"ger\"", ctx));
        assertFalse(eval("answer(party) == \"fra\"", ctx));
    }

    @Test
    void evaluate_equalsTextIgnoresCase() throws Exception {
        assertTrue(eval("answer(q) == \"yes\"", ctx(Map.of("q", new Answer.Text("Yes")))));
    }

    @Test
    void evaluate_notEqualsFalseWhenUnanswered() throws Exception {
        assertFalse(eval("answer(q) != \"a\"", ctx(Map.of())));
        assertTrue(eval("answer(q) != \"a\"", ctx(Map.of("q", new Answer.Choice("b")))));
    }

    @Test
    void evaluate_skippedIsUnanswered() throws Exception {
        ConditionContext ctx = ctx(Map.of("q", new Answer.Skipped()));
        assertFalse(eval("answer(q) answered", ctx));
        assertFalse(eval("answer(q) != \"a\"", ctx));
    }

    @Test
    void evaluate_answeredTrueWhenAnswered() throws Exception {
        assertTrue(eval("answer(q) answered", ctx(Map.of("q", new Answer.Text("x")))));
    }

    @Test
    void evaluate_hasOnChoices() throws Exception {
        ConditionContext ctx = ctx(Map.of("q", new Answer.Choices(new LinkedHashSet<>(Set.of("a", "b")))));
        assertTrue(eval("answer(q) has \"a\"", ctx));
        assertFalse(eval("answer(q) has \"c\"", ctx));
    }

    @Test
    void evaluate_inList() throws Exception {
        ConditionContext ctx = ctx(Map.of("q", new Answer.Choice("b")));
        assertTrue(eval("answer(q) in [\"a\", \"b\"]", ctx));
        assertFalse(eval("answer(q) in [\"a\", \"c\"]", ctx));
    }

    @Test
    void evaluate_permission() throws Exception {
        assertTrue(eval("permission(\"x.y\")", ctx(Map.of(), Set.of("x.y"))));
        assertFalse(eval("permission(\"x.y\")", ctx(Map.of())));
    }

    @Test
    void evaluate_combinators() throws Exception {
        ConditionContext ctx = ctx(Map.of("q", new Answer.Choice("a")));
        assertTrue(eval("not answer(q) == \"b\" and answer(q) == \"a\"", ctx));
        assertTrue(eval("answer(q) == \"b\" or answer(q) == \"a\"", ctx));
    }

    @Test
    void references_collectsQuestionsAndValues() throws Exception {
        Condition c = ConditionParser.parse(
                "answer(a) == \"x\" and (answer(b) in [\"y\", \"z\"] or not answer(a) != \"w\") and answer(c) answered");
        assertEquals(Map.of("a", Set.of("x", "w"), "b", Set.of("y", "z"), "c", Set.of()), c.references());
    }
}
