package com.example.backend.controller;

import com.example.backend.dto.AuthRequest;
import com.example.backend.entity.User;
import com.example.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*");
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public String register(@RequestBody AuthRequest request) {
        return authService.register(request.getEmail(), request.getPassword());
    }

    @PostMapping("/login")
    public String login(@RequestBody AuthRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return authService.getAllUsers();
    }
}
