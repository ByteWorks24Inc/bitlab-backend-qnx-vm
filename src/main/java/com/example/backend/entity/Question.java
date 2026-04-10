package com.example.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.*;

@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String questionName;
    private String questionDescription;
    private List<TestCase> testCaseList;
    private List<String> tags;
    private Long InputTextId;
    private Long OutputTextId;
}
