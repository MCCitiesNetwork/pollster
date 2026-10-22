package com.minecraftcitiesnetwork.pollster.api.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Splits a condition into tokens. Columns are 1-based. */
final class Lexer {

    private static final Map<String, Token.Type> KEYWORDS = Map.of(
            "and", Token.Type.AND,
            "or", Token.Type.OR,
            "not", Token.Type.NOT,
            "in", Token.Type.IN,
            "has", Token.Type.HAS,
            "answered", Token.Type.ANSWERED,
            "answer", Token.Type.ANSWER,
            "permission", Token.Type.PERMISSION);

    private final String source;
    private int pos;

    private Lexer(String source) {
        this.source = source;
    }

    static List<Token> tokenize(String source) throws ConditionParseException {
        return new Lexer(source).run();
    }

    private List<Token> run() throws ConditionParseException {
        List<Token> tokens = new ArrayList<>();
        // The end-of-input column sits just after the last token, so "answer(q) == " reports
        // the spot where the missing value should start rather than trailing whitespace.
        int endOfLastToken = 1;
        while (true) {
            skipWhitespace();
            if (this.pos >= this.source.length()) {
                tokens.add(new Token(Token.Type.EOF, "", endOfLastToken));
                return tokens;
            }
            tokens.add(next());
            endOfLastToken = this.pos + 1;
        }
    }

    private void skipWhitespace() {
        while (this.pos < this.source.length() && Character.isWhitespace(this.source.charAt(this.pos))) {
            this.pos++;
        }
    }

    private Token next() throws ConditionParseException {
        int column = this.pos + 1;
        char c = this.source.charAt(this.pos);
        switch (c) {
            case '(' -> {
                this.pos++;
                return new Token(Token.Type.LPAREN, "(", column);
            }
            case ')' -> {
                this.pos++;
                return new Token(Token.Type.RPAREN, ")", column);
            }
            case '[' -> {
                this.pos++;
                return new Token(Token.Type.LBRACKET, "[", column);
            }
            case ']' -> {
                this.pos++;
                return new Token(Token.Type.RBRACKET, "]", column);
            }
            case ',' -> {
                this.pos++;
                return new Token(Token.Type.COMMA, ",", column);
            }
            case '"' -> {
                return string(column);
            }
            case '=' -> {
                return twoCharOperator('=', Token.Type.EQ, "==", column);
            }
            case '!' -> {
                return twoCharOperator('=', Token.Type.NE, "!=", column);
            }
            default -> {
                if (isWordChar(c)) {
                    return word(column);
                }
                throw new ConditionParseException("Unexpected character '" + c + "'", column);
            }
        }
    }

    private Token twoCharOperator(char second, Token.Type type, String text, int column)
            throws ConditionParseException {
        if (this.pos + 1 < this.source.length() && this.source.charAt(this.pos + 1) == second) {
            this.pos += 2;
            return new Token(type, text, column);
        }
        throw new ConditionParseException("Unknown operator '" + this.source.charAt(this.pos) + "'", column);
    }

    private static boolean isWordChar(char c) {
        // Hyphens and digits are allowed because question keys are slugs such as "party-reason".
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private Token word(int column) {
        int start = this.pos;
        while (this.pos < this.source.length() && isWordChar(this.source.charAt(this.pos))) {
            this.pos++;
        }
        String text = this.source.substring(start, this.pos);
        Token.Type type = KEYWORDS.getOrDefault(text, Token.Type.IDENTIFIER);
        return new Token(type, text, column);
    }

    private Token string(int column) throws ConditionParseException {
        StringBuilder value = new StringBuilder();
        this.pos++; // opening quote
        while (this.pos < this.source.length()) {
            char c = this.source.charAt(this.pos);
            if (c == '"') {
                this.pos++;
                return new Token(Token.Type.STRING, value.toString(), column);
            }
            if (c == '\\') {
                if (this.pos + 1 >= this.source.length()) {
                    break;
                }
                char escaped = this.source.charAt(this.pos + 1);
                if (escaped != '"' && escaped != '\\') {
                    throw new ConditionParseException("Unknown escape '\\" + escaped + "'", this.pos + 1);
                }
                value.append(escaped);
                this.pos += 2;
                continue;
            }
            value.append(c);
            this.pos++;
        }
        throw new ConditionParseException("Unterminated string", column);
    }
}
