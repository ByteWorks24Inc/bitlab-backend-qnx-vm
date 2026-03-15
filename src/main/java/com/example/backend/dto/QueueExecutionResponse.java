package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueueExecutionResponse {

    private String status;

    private String jobId;
}
