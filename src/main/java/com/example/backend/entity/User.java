package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@AllArgsConstructor   // ✅ add this
@NoArgsConstructor    // ✅ add this
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private LocalDateTime createdAt;

    private int questionSolved;
    private int testCaseSolved;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}