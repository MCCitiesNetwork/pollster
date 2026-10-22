package com.minecraftcitiesnetwork.pollster.api.condition;

/** Thrown when a {@code show-if} expression is malformed. */
public final class ConditionParseException extends Exception {

    private final int column;

    public ConditionParseException(String problem, int column) {
        super(problem + " at column " + column);
        this.column = column;
    }

    /** The 1-based column in the source where the problem was found. */
    public int column() {
        return this.column;
    }
}
