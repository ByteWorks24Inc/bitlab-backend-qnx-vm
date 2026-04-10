package com.example.backend.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String questionName;
    private String questionDescription;
    @OneToMany
    private List<TestCase> testCaseList;
    @ElementCollection
    private List<String> tags;
    private Long InputTextId;
    private Long OutputTextId;
}
