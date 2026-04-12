package com.example.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long questionId;
    @Column(columnDefinition = "TEXT")
    private String code;
    private String status; //PASSED, FAILED, ERROR
    private int passedTestCases;
    private int totalTestCases;
    private LocalDateTime submittedAt;
}
