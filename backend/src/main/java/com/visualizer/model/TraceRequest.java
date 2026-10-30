package com.visualizer.model;

public record TraceRequest(
        String language,
        String code,
        String input,
        int maxSteps
) {
    public TraceRequest {
        if (maxSteps <= 0) {
            maxSteps = 1000;
        }
    }

    public TraceRequest(String language, String code, String input) {
        this(language, code, input, 1000);
    }
}
