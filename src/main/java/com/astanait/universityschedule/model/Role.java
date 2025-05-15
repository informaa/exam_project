package com.astanait.universityschedule.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

// Аннотации над классом
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor

// будут храниться данные об одной роли
public class Role {

    // Поле id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Поле name
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    public Role(String name) {
        this.name = name;
    }
}
