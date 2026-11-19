package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of the live C code-quality analysis shown in the QNX lab. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeQualityReport {

    private int score;          // 0-100
    private String grade;       // Excellent / Good / Fair / Needs work / Poor
    private int lines;          // total source lines
    private int functions;      // detected function definitions
    private double commentRatio; // percentage of comment lines
    private int maxNesting;     // deepest brace nesting
    private int errorCount;
    private int warningCount;
    private int infoCount;
    private List<Finding> findings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Finding {
        private String severity; // error | warning | info
        private String rule;
        private String message;
        private Integer line;    // 1-based, null for file-level findings
    }
}
