package com.shardbound.rules;

/** Comparison operators used by conditions; symbols match the SQL CHECK list. */
public enum Op {
    EQ("="), NE("!="), GT(">"), GE(">="), LT("<"), LE("<=");

    private final String symbol;

    Op(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** Applies the operator: {@code actual <op> expected}. O(1). */
    public boolean compare(int actual, int expected) {
        return switch (this) {
            case EQ -> actual == expected;
            case NE -> actual != expected;
            case GT -> actual > expected;
            case GE -> actual >= expected;
            case LT -> actual < expected;
            case LE -> actual <= expected;
        };
    }

    /** Human-readable symbol for lock reasons ("≥" instead of ">="). */
    public String pretty() {
        return switch (this) {
            case EQ -> "=";
            case NE -> "≠";
            case GT -> ">";
            case GE -> "≥";
            case LT -> "<";
            case LE -> "≤";
        };
    }

    /** Parses "=", "!=", ">", ">=", "<", "<=". Time O(1) (6 constants). */
    public static Op fromSymbol(String symbol) {
        for (Op op : values()) {
            if (op.symbol.equals(symbol)) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown operator: " + symbol);
    }
}
