package com.example.backend.service;

import com.example.backend.dto.ExecutionRequest;
import com.example.backend.dto.QueueExecutionResponse;
import com.example.backend.dto.KafkaExecutionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final SqsJobService sqsJobService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Object execute(ExecutionRequest request) throws Exception {

        String language = request.getLanguage().toLowerCase();

        // ================= QNX =================
        if (language.equals("qnx")) {
            String jobId = UUID.randomUUID().toString();
            sqsJobService.sendJob(
                    jobId,
                    "qnx",
                    request.getDesignCode()
            );
            return QueueExecutionResponse.builder()
                    .status("queued")
                    .jobId(jobId)
                    .build();
        }

        // ================= VERILOG, VHDL, SYSTEMVERILOG =================
        if (language.equals("verilog") || language.equals("vhdl") || language.equals("systemverilog")) {
            String jobId = UUID.randomUUID().toString();
            
            KafkaExecutionMessage message = new KafkaExecutionMessage();
            message.setJobId(jobId);
            message.setLanguage(language);
            message.setDesignCode(request.getDesignCode());
            message.setTestbenchCode(request.getTestbenchCode());
            
            kafkaTemplate.send("execution_requests_" + language, objectMapper.writeValueAsString(message));

            return QueueExecutionResponse.builder()
                    .status("queued")
                    .jobId(jobId)
                    .build();
        }

        throw new RuntimeException("Invalid language");
    }
}
}
