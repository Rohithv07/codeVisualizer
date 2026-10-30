package com.visualizer.runner;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;

public interface LanguageRunner {
    boolean supports(String language);
    TraceResponse run(TraceRequest request) throws Exception;
}

