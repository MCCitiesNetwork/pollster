package com.minecraftcitiesnetwork.pollster.api.condition;

/** One lexical unit. {@code text} is the unescaped value for strings and the source text otherwise. */
record Token(Type type, String text, int column) {

    enum Type {
        IDENTIFIER,
        STRING,
        LPAREN,
        RPAREN,
        LBRACKET,
        RBRACKET,
        COMMA,
        EQ,
        NE,
        AND,
        OR,
        NOT,
        IN,
        HAS,
        ANSWERED,
        ANSWER,
        PERMISSION,
        EOF
    }
}
