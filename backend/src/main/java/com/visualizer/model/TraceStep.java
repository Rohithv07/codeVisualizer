package com.visualizer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TraceStep(
        int step,
        int line,
        String event,
        String function,
        List<StackFrame> callStack,
        Map<String, VariableValue> variables,
        String stdoutDelta,
        String totalStdout
) {}
