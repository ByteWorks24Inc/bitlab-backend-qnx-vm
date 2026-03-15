package com.example.backend.service;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public String register(String email, String password) {

        if (userRepository.findByEmail(email).isPresent()) {
            return "Email already exists";
        }

        User user = User.builder()
                .email(email)
                .password(password)
                .build();

        userRepository.save(user);

        return "User registered successfully";
    }
    public String login(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!password.equals(user.getPassword())) {
            return "Invalid password";
        }

        return "Login successful";
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}