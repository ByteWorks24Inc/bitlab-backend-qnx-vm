package com.example.backend.controller;

import com.example.backend.entity.Question;
import com.example.backend.entity.TestCase;
import com.example.backend.service.QuestionService;
import com.example.backend.service.TestCaseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class QuestionController {
    private final QuestionService questionService;
    private final TestCaseService testCaseService;
    public QuestionController(QuestionService questionService, TestCaseService testCaseService){
        this.questionService = questionService;
        this.testCaseService = testCaseService;
    }
    @GetMapping("/question")
    public List<Question> getAll1(){
        return questionService.getAll();
    }
    @GetMapping("/testcase")
    public List<TestCase> getAll2(){
        return testCaseService.getAll();
    }
    @GetMapping("/question/{id}")
    public Optional<Question> getQuestionById(@PathVariable Long id){
        return questionService.getQuestionById(id);
    }
    @GetMapping("/testcase/{id}")
    public Optional<TestCase> getTestCaseById(@PathVariable Long id){
        return testCaseService.getTestCaseById(id);
    }
}
