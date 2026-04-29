package com.example.backend.dto;

import lombok.Data;

@Data
public class KafkaExecutionMessage {
    private String jobId;
    private String language;
    private String designCode;
    private String testbenchCode;
}
