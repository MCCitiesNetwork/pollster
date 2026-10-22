package com.minecraftcitiesnetwork.pollster.api.condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for {@code show-if} expressions. It checks syntax only; whether a
 * question or option exists is checked later against the survey.
 */
public final class ConditionParser {

    private final List<Token> tokens;
    private static final int MAX_DEPTH = 32;

    private int index;
    private int depth;

    private ConditionParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Condition parse(String source) throws ConditionParseException {
        ConditionParser parser = new ConditionParser(Lexer.tokenize(source));
        Condition result = parser.or();
        Token rest = parser.peek();
        if (rest.type() != Token.Type.EOF) {
            throw new ConditionParseException("Unexpected '" + rest.text() + "'", rest.column());
        }
        return result;
    }

    private Condition or() throws ConditionParseException {
        Condition left = and();
        while (peek().type() == Token.Type.OR) {
            advance();
            left = new Condition.Or(left, and());
        }
        return left;
    }

    private Condition and() throws ConditionParseException {
        Condition left = not();
        while (peek().type() == Token.Type.AND) {
            advance();
            left = new Condition.And(left, not());
        }
        return left;
    }

    private Condition not() throws ConditionParseException {
        if (peek().type() == Token.Type.NOT) {
            advance();
            enter(peek());
            try {
                return new Condition.Not(not());
            } finally {
                this.depth--;
            }
        }
        return atom();
    }

    private Condition atom() throws ConditionParseException {
        Token token = peek();
        switch (token.type()) {
            case LPAREN -> {
                advance();
                enter(token);
                Condition inner = or();
                expect(Token.Type.RPAREN, "')'");
                this.depth--;
                return inner;
            }
            case PERMISSION -> {
                advance();
                expect(Token.Type.LPAREN, "'('");
                String node = expect(Token.Type.STRING, "a quoted permission node").text();
                expect(Token.Type.RPAREN, "')'");
                return new Condition.HasPermission(node, token.column());
            }
            case ANSWER -> {
                return comparison();
            }
            default -> throw new ConditionParseException(
                    "Expected 'answer(', 'permission(', 'not' or '('" + found(token), token.column());
        }
    }

    private Condition comparison() throws ConditionParseException {
        Token start = advance();
        expect(Token.Type.LPAREN, "'('");
        String question = expect(Token.Type.IDENTIFIER, "a question key").text();
        expect(Token.Type.RPAREN, "')'");

        Token op = peek();
        int column = start.column();
        switch (op.type()) {
            case EQ -> {
                advance();
                return new Condition.Equals(question, string(), column);
            }
            case NE -> {
                advance();
                return new Condition.NotEquals(question, string(), column);
            }
            case HAS -> {
                advance();
                return new Condition.Has(question, string(), column);
            }
            case ANSWERED -> {
                advance();
                return new Condition.Answered(question, column);
            }
            case IN -> {
                advance();
                return new Condition.In(question, stringList(), column);
            }
            default -> throw new ConditionParseException(
                    "Expected '==', '!=', 'in', 'has' or 'answered'" + found(op), op.column());
        }
    }

    private String string() throws ConditionParseException {
        return expect(Token.Type.STRING, "a quoted string").text();
    }

    private List<String> stringList() throws ConditionParseException {
        expect(Token.Type.LBRACKET, "'['");
        List<String> values = new ArrayList<>();
        values.add(string());
        while (peek().type() == Token.Type.COMMA) {
            advance();
            values.add(string());
        }
        expect(Token.Type.RBRACKET, "']'");
        return values;
    }

    // Bounds recursion so a hand-edited file cannot overflow the stack.
    private void enter(Token at) throws ConditionParseException {
        this.depth++;
        if (this.depth > MAX_DEPTH) {
            throw new ConditionParseException("Nested more than " + MAX_DEPTH + " levels deep", at.column());
        }
    }

    private Token peek() {
        return this.tokens.get(this.index);
    }

    private Token advance() {
        Token token = this.tokens.get(this.index);
        // Never step past EOF so repeated peeks after an error stay safe.
        if (token.type() != Token.Type.EOF) {
            this.index++;
        }
        return token;
    }

    private Token expect(Token.Type type, String description) throws ConditionParseException {
        Token token = peek();
        if (token.type() != type) {
            throw new ConditionParseException("Expected " + description, token.column());
        }
        return advance();
    }

    private static String found(Token token) {
        return token.type() == Token.Type.EOF ? ", found end of input" : ", found '" + token.text() + "'";
    }
}
