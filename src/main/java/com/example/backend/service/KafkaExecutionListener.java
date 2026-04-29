package com.example.backend.service;

import com.example.backend.dto.ExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper service for extracting error line information from compiler output.
 * Kafka listeners removed — execution is now done directly via ProcessBuilder in ExecutionService.
 */
@Service
@RequiredArgsConstructor
public class KafkaExecutionListener {

    public String extractVerilogError(String logs) {
        // iverilog output format: design.v:2: syntax error
        Pattern pattern = Pattern.compile("(?:design\\.v|tb\\.v):(\\d+):.*error", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(logs);
        if (matcher.find()) {
            return "Error at line " + matcher.group(1);
        }
        return null;
    }

    public String extractVhdlError(String logs) {
        // ghdl output format: design.vhd:10:14: error: ...
        Pattern pattern = Pattern.compile("(?:design\\.vhd|tb\\.vhd):(\\d+):.*error", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(logs);
        if (matcher.find()) {
            return "Error at line " + matcher.group(1);
        }
        return null;
    }
}
