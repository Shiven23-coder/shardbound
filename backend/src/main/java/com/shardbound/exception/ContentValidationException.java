package com.shardbound.exception;

import java.util.List;

/**
 * Story content is inconsistent (missing ids, quest cycle, validator errors).
 * Only thrown at startup or import, never during play.
 */
public class ContentValidationException extends GameException {

    private final List<String> problems;

    public ContentValidationException(String message) {
        this(message, List.of());
    }

    public ContentValidationException(String message, List<String> problems) {
        super(problems.isEmpty() ? message : message + "\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }

    @Override
    public String code() {
        return "CONTENT_INVALID";
    }
}
