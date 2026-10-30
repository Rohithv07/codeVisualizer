package com.visualizer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceResponse(
        String status,
        String language,
        String error,
        int totalSteps,
        String stdout,
        List<TraceStep> steps
) {
    public static TraceResponse error(String status, String language, String errorMessage) {
        return new TraceResponse(status, language, errorMessage, 0, "", List.of());
    }
}
