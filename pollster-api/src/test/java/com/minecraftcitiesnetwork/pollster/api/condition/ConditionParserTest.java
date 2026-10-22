package com.minecraftcitiesnetwork.pollster.api.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.minecraftcitiesnetwork.pollster.api.condition.Condition.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionParserTest {

    private static ConditionParseException fails(String source) {
        return assertThrows(ConditionParseException.class, () -> ConditionParser.parse(source));
    }

    @Test
    void parse_equals() throws Exception {
        assertEquals(new Equals("party", "ger", 1), ConditionParser.parse("answer(party) == \"ger\""));
    }

    @Test
    void parse_notEquals() throws Exception {
        assertEquals(new NotEquals("party", "ger", 1), ConditionParser.parse("answer(party) != \"ger\""));
    }

    @Test
    void parse_precedence() throws Exception {
        Condition parsed = ConditionParser.parse(
                "answer(a) == \"x\" or answer(b) == \"y\" and not permission(\"p\")");
        assertEquals(
                new Or(
                        new Equals("a", "x", 1),
                        new And(new Equals("b", "y", 21), new Not(new HasPermission("p", 46)))),
                parsed);
    }

    @Test
    void parse_parentheses() throws Exception {
        Condition parsed = ConditionParser.parse("(answer(a) answered or answer(b) answered) and answer(c) answered");
        assertEquals(
                new And(new Or(new Answered("a", 2), new Answered("b", 24)), new Answered("c", 46)), parsed);
    }

    @Test
    void parse_inList() throws Exception {
        assertEquals(
                new In("q", List.of("a", "b"), 1), ConditionParser.parse("answer(q) in [\"a\", \"b\"]"));
    }

    @Test
    void parse_has() throws Exception {
        assertEquals(new Has("issues", "tax", 1), ConditionParser.parse("answer(issues) has \"tax\""));
    }

    @Test
    void parse_answered() throws Exception {
        assertEquals(new Answered("q", 1), ConditionParser.parse("answer(q) answered"));
    }

    @Test
    void parse_hyphenatedKey() throws Exception {
        assertEquals(new Answered("party-reason", 1), ConditionParser.parse("answer(party-reason) answered"));
    }

    @Test
    void parse_doubleNot() throws Exception {
        assertEquals(
                new Not(new Not(new HasPermission("p", 9))), ConditionParser.parse("not not permission(\"p\")"));
    }

    @Test
    void parse_escapedQuote() throws Exception {
        assertEquals(
                new Equals("q", "say \"hi\"", 1), ConditionParser.parse("answer(q) == \"say \\\"hi\\\"\""));
    }

    @Test
    void parse_escapedBackslash() throws Exception {
        assertEquals(new Equals("q", "a\\b", 1), ConditionParser.parse("answer(q) == \"a\\\\b\""));
    }

    @Test
    void parse_reportsColumn() {
        ConditionParseException e = fails("answer(q) == ");
        assertEquals(13, e.column());
    }

    @Test
    void parse_messageIncludesColumn() {
        ConditionParseException e = fails("answer(q == \"a\"");
        assertEquals("Expected ')' at column 10", e.getMessage());
    }

    @Test
    void parse_rejectsUnknownOperator() {
        assertEquals(11, fails("answer(q) >= \"a\"").column());
    }

    @Test
    void parse_rejectsTrailingInput() {
        assertEquals(20, fails("answer(q) answered answered").column());
    }

    @Test
    void parse_rejectsEmptyInList() {
        fails("answer(q) in []");
    }

    @Test
    void parse_rejectsUnterminatedString() {
        assertEquals(14, fails("answer(q) == \"abc").column());
    }

    @Test
    void parse_rejectsBlank() {
        fails("   ");
    }

    @Test
    void parse_rejectsAnsweredWithValue() {
        fails("answer(q) answered \"a\"");
    }

    @Test
    void parse_rejectsUnbalancedParenthesis() {
        fails("(answer(q) answered");
    }
}
