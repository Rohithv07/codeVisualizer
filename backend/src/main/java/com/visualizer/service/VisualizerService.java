package com.visualizer.service;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import com.visualizer.runner.LanguageRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisualizerService {

    private final List<LanguageRunner> runners;

    public VisualizerService(List<LanguageRunner> runners) {
        this.runners = runners;
    }

    public TraceResponse trace(TraceRequest request) throws Exception {
        String lang = request.language();
        if (lang == null || lang.trim().isEmpty()) {
            return TraceResponse.error("INVALID_REQUEST", "unknown", "Language must be specified (python, java, cpp).");
        }

        for (LanguageRunner runner : runners) {
            if (runner.supports(lang)) {
                return runner.run(request);
            }
        }

        return TraceResponse.error("UNSUPPORTED_LANGUAGE", lang,
                "Language '" + lang + "' is not supported yet. Available: python, java.");
    }
}

