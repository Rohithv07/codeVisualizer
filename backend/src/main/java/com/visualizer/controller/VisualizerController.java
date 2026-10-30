package com.visualizer.controller;

import com.visualizer.model.TraceRequest;
import com.visualizer.model.TraceResponse;
import com.visualizer.service.VisualizerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class VisualizerController {

    private final VisualizerService visualizerService;

    public VisualizerController(VisualizerService visualizerService) {
        this.visualizerService = visualizerService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Code Visualizer Engine",
                "supportedLanguages", new String[]{"python", "java", "cpp"}
        ));
    }

    @PostMapping("/trace")
    public ResponseEntity<TraceResponse> trace(@RequestBody TraceRequest request) {
        try {
            TraceResponse response = visualizerService.trace(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(TraceResponse.error("INTERNAL_ERROR",
                    request.language() != null ? request.language() : "unknown",
                    e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }
}

