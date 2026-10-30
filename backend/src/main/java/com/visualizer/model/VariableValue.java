package com.visualizer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VariableValue(
        String type,
        Object value,
        String repr,
        Integer length,
        Boolean isMatrix,
        List<VariableValue> values,
        Map<String, VariableValue> fields,
        Map<String, VariableValue> entries,
        String className,
        String id
) {
    public VariableValue(String type, Object value, String repr) {
        this(type, value, repr, null, null, null, null, null, null, null);
    }

    public static VariableValue array(int length, boolean isMatrix, List<VariableValue> values) {
        return new VariableValue("array", null, "Array[" + length + "]", length, isMatrix, values, null, null, null, null);
    }

    public static VariableValue object(String className, String id, Map<String, VariableValue> fields) {
        return new VariableValue("object", null, className + "@" + id, null, null, null, fields, null, className, id);
    }
}
