package com.example.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionResponse {
    private String status;
    private String logs;
    private String vcdBase64;
}