package com.example.backend.dto;
import lombok.Data;

@Data
public class ExecutionRequest {
    private String language;   // verilog | vhdl | qnx
    private String designCode;
    private String testbenchCode;
}