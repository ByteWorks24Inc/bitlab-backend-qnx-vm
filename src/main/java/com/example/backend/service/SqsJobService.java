package com.example.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class SqsJobService {

    private final SqsClient sqsClient;

    private final String queueUrl =
            "https://sqs.us-east-2.amazonaws.com/160927904719/code-execution-queue";

    private final ObjectMapper mapper = new ObjectMapper();

    public void sendJob(String jobId, String language, String code) {

        try {

            Map<String, String> payload = new HashMap<>();
            payload.put("jobId", jobId);
            payload.put("language", language);
            payload.put("code", code);

            String message = mapper.writeValueAsString(payload);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message)
                    .build();

            sqsClient.sendMessage(request);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
