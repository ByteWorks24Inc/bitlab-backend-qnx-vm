package com.example.backend.dto;

import lombok.Data;

@Data
public class ExecutionResultRequest {

    private String jobId;
    private String logs;

}
