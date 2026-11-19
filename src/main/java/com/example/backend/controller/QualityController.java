package com.example.backend.controller;

import com.example.backend.dto.CodeQualityReport;
import com.example.backend.service.CodeQualityAnalyzer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Live C quality analysis used by the QNX lab as the user types. */
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final CodeQualityAnalyzer analyzer;

    @PostMapping("/c")
    public CodeQualityReport analyzeC(@RequestBody Map<String, String> body) {
        return analyzer.analyze(body.get("code"));
    }
}
