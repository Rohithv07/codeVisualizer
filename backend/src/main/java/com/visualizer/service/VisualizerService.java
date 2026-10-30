package com.visualizer.service;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import com.visualizer.runner.LanguageRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VisualizerService {

    private final List<LanguageRunner> runners;
    private final LeetCodeHarnessService harnessService;

    public VisualizerService(List<LanguageRunner> runners, LeetCodeHarnessService harnessService) {
        this.runners = runners;
        this.harnessService = harnessService;
    }

    public TraceResponse trace(TraceRequest request) throws Exception {
        String lang = request.language();
        if (lang == null || lang.trim().isEmpty()) {
            return TraceResponse.error("INVALID_REQUEST", "unknown", "Language must be specified (python, java, cpp).");
        }

        // Apply LeetCode auto-harnessing if code lacks main()
        String processedCode = request.code();
        if (harnessService.isLeetCodeStyle(processedCode)) {
            if ("java".equalsIgnoreCase(lang)) {
                processedCode = harnessService.augmentJava(processedCode, request.input());
            } else if ("cpp".equalsIgnoreCase(lang) || "c++".equalsIgnoreCase(lang) || "c".equalsIgnoreCase(lang)) {
                processedCode = harnessService.augmentCpp(processedCode, request.input());
            } else if ("python".equalsIgnoreCase(lang) || "py".equalsIgnoreCase(lang)) {
                processedCode = harnessService.augmentPython(processedCode, request.input());
            }
        }

        TraceRequest finalRequest = new TraceRequest(lang, processedCode, request.input(), request.maxSteps());

        for (LanguageRunner runner : runners) {
            if (runner.supports(lang)) {
                return runner.run(finalRequest);
            }
        }

        return TraceResponse.error("UNSUPPORTED_LANGUAGE", lang,
                "Language '" + lang + "' is not supported yet. Available: python, java, cpp.");
    }
}
