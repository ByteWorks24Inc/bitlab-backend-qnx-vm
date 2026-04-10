package com.example.backend.service;

import com.example.backend.entity.TestCase;
import com.example.backend.repository.TestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TestCaseService {
    private final TestCaseRepository testCaseRepository;

    public TestCaseService(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    public List<TestCase> getAll(){
        return testCaseRepository.findAll();
    }
    public Optional<TestCase> getTestCaseById(Long id){
        return testCaseRepository.findById(id);
    }
}
