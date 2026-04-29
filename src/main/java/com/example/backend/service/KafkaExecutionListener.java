package com.example.backend.service;

import com.example.backend.dto.ExecutionResult;
import com.example.backend.dto.KafkaExecutionMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class KafkaExecutionListener {

    private final VerilogVhdlExecutor executor;
    private final ExecutionResultStore store;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"execution_requests_verilog", "execution_requests_systemverilog"}, groupId = "verilog-group")
    public void listenVerilog(String messageStr) {
        processMessage(messageStr, "verilog");
    }

    @KafkaListener(topics = "execution_requests_vhdl", groupId = "vhdl-group")
    public void listenVhdl(String messageStr) {
        processMessage(messageStr, "vhdl");
    }

    private void processMessage(String messageStr, String expectedLang) {
        try {
            KafkaExecutionMessage message = objectMapper.readValue(messageStr, KafkaExecutionMessage.class);
            String jobId = message.getJobId();
            String language = message.getLanguage();
            String workDir = "workspace/" + UUID.randomUUID();

            String logs = "";
            String vcdBase64 = null;
            String errorLine = null;

            try {
                if (language.equals("verilog") || language.equals("systemverilog")) {
                    logs = executor.executeVerilog(workDir, message.getDesignCode(), message.getTestbenchCode());
                    vcdBase64 = executor.encodeVcdIfExists(workDir, "demo.vcd");
                    errorLine = extractVerilogError(logs);
                } else if (language.equals("vhdl")) {
                    logs = executor.executeVhdl(workDir, message.getDesignCode(), message.getTestbenchCode());
                    vcdBase64 = executor.encodeVcdIfExists(workDir, "demo.vcd");
                    errorLine = extractVhdlError(logs);
                }
            } catch (Exception e) {
                logs += "\nExecution failed: " + e.getMessage();
            }

            ExecutionResult result = ExecutionResult.builder()
                    .status("DONE")
                    .logs(logs)
                    .vcdBase64(vcdBase64)
                    .errorLine(errorLine)
                    .build();

            store.save(jobId, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractVerilogError(String logs) {
        // iverilog output format: design.v:2: syntax error
        Pattern pattern = Pattern.compile("(?:design\\.v|tb\\.v):(\\d+):.*error", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(logs);
        if (matcher.find()) {
            return "Error at line " + matcher.group(1);
        }
        return null;
    }

    private String extractVhdlError(String logs) {
        // ghdl output format: design.vhd:10:14: error: ...
        Pattern pattern = Pattern.compile("(?:design\\.vhd|tb\\.vhd):(\\d+):.*error", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(logs);
        if (matcher.find()) {
            return "Error at line " + matcher.group(1);
        }
        return null;
    }
}
