package com.example.backend.service;

import com.example.backend.entity.Question;
import com.example.backend.repository.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Optional;

@Service
public class QuestionService {
    private final QuestionRepository questionRepository;


    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }
    public Optional<Question> getQuestionById(Long id){
        return questionRepository.findById(id);
    }

    public List<Question> getAll(){
        return questionRepository.findAll();
    }
}
