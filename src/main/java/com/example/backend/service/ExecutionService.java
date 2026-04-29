package com.example.backend.service;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.dto.ExecutionResult;
import com.example.backend.dto.QueueExecutionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final SqsJobService sqsJobService;
    private final VerilogVhdlExecutor verilogVhdlExecutor;
    private final ExecutionResultStore resultStore;
    private final KafkaExecutionListener errorExtractor;

    @Value("${execution.workdir.base:${user.home}/bitlab-jobs}")
    private String workDirBase;

    public Object execute(ExecutionRequest request) throws Exception {

        String language = request.getLanguage().toLowerCase();

        // ================= QNX → AWS SQS =================
        if (language.equals("qnx")) {
            String jobId = UUID.randomUUID().toString();
            sqsJobService.sendJob(jobId, "qnx", request.getDesignCode());
            resultStore.save(jobId, ExecutionResult.builder().status("RUNNING").logs("").build());
            return QueueExecutionResponse.builder().status("queued").jobId(jobId).build();
        }

        // ================= VERILOG / VHDL / SYSTEMVERILOG → direct ProcessBuilder =================
        if (language.equals("verilog") || language.equals("vhdl") || language.equals("systemverilog")) {
            String jobId = UUID.randomUUID().toString();
            String workDir = workDirBase + "/" + jobId;

            // Mark as running immediately so frontend can poll
            resultStore.save(jobId, ExecutionResult.builder().status("RUNNING").logs("").build());

            // Run asynchronously so the HTTP request returns immediately
            String finalLanguage = language;
            CompletableFuture.runAsync(() -> {
                try {
                    String logs;
                    if (finalLanguage.equals("vhdl")) {
                        logs = verilogVhdlExecutor.executeVhdl(workDir, request.getDesignCode(), request.getTestbenchCode());
                    } else {
                        // verilog and systemverilog both use iverilog
                        logs = verilogVhdlExecutor.executeVerilog(workDir, request.getDesignCode(), request.getTestbenchCode());
                    }
                    String vcdBase64 = verilogVhdlExecutor.encodeVcdIfExists(workDir, "demo.vcd");
                    String errorLine = finalLanguage.equals("vhdl")
                            ? errorExtractor.extractVhdlError(logs)
                            : errorExtractor.extractVerilogError(logs);
                    resultStore.save(jobId, ExecutionResult.builder()
                            .status("DONE")
                            .logs(logs)
                            .vcdBase64(vcdBase64)
                            .errorLine(errorLine)
                            .build());
                } catch (Exception e) {
                    resultStore.save(jobId, ExecutionResult.builder()
                            .status("DONE")
                            .logs("Execution error: " + e.getMessage())
                            .build());
                }
            });

            return QueueExecutionResponse.builder().status("queued").jobId(jobId).build();
        }

        throw new RuntimeException("Invalid language: " + language);
    }
}
